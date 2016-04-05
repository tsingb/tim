package com.tsingb.tim.shard.service

import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.concurrent.Await
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.NoSerializationVerificationNeeded
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.cluster.Cluster
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.persistence.SnapshotOffer
import akka.actor.RootActorPath
import akka.actor.ActorSelection
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.ClusterEvent.ClusterShuttingDown
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.MemberStatus
import akka.cluster.Member
import akka.cluster.singleton.ClusterSingletonManagerSettings

/**
 *
 *
 *
 *
 *
 */
object SeviceExtension extends ExtensionId[SeviceExtension] with ExtensionIdProvider {
  override def get(system: ActorSystem) = super.get(system)
  override def lookup = SeviceExtension
  override def createExtension(system: ExtendedActorSystem) = new SeviceExtension(system)

}

class SeviceExtension(system: ExtendedActorSystem) extends Extension with Serializable {
  private val cluster = Cluster(system)

  private[service] object Settings {
    val config = system.settings.config.getConfig("com.tsingb.tim.service")
    val Role: Option[String] = config.getString("role") match {
      case "" => None
      case r  => Some(r)
    }
    val HasNecessaryClusterRole: Boolean = Role.forall(cluster.selfRoles.contains)
    val GuardianName: String = config.getString("guardian-name")
    val CoordinatorFailureBackoff = config.getDuration("coordinator-failure-backoff", MILLISECONDS).millis
    val SnapshotInterval: FiniteDuration = config.getDuration("snapshot-interval", MILLISECONDS).millis
    val RetryInterval: FiniteDuration = config.getDuration("retry-interval", MILLISECONDS).millis
    val HandOffTimeout: FiniteDuration = config.getDuration("handoff-timeout", MILLISECONDS).millis
    val serviceSize: Int = config.getInt("service-size")
  }

  import Settings._
  import SeviceGuardian._
  private val regions: ConcurrentHashMap[String, ActorRef] = new ConcurrentHashMap
  private lazy val guardian = system.actorOf(Props[SeviceGuardian], Settings.GuardianName)

  def start(
    typeName: String,
    entryProps: Option[Props]): ActorRef = {
    implicit val timeout = system.settings.CreationTimeout
    val startMsg = Start(typeName, entryProps)
    val Started(serviceRegion) = Await.result(guardian ? startMsg, timeout.duration)
    regions.put(typeName, serviceRegion)
    serviceRegion
  }

  def serviceRegion(typeName: String): ActorRef = regions.get(typeName) match {
    case null => throw new IllegalArgumentException(s"Shard type [$typeName] must be started first")
    case ref  => ref
  }

}

private[service] object SeviceGuardian {
  case class Start(typeName: String, entryProps: Option[Props])
    extends NoSerializationVerificationNeeded
  case class Started(shardRegion: ActorRef) extends NoSerializationVerificationNeeded
}

/**
 * 守护Actor
 */
private[service] class SeviceGuardian extends Actor with ActorLogging {

  import SeviceGuardian._
  val Sevice = SeviceExtension(context.system)
  import Sevice.Settings._

  val cluster = Cluster(context.system)

  def receive = {
    case Start(typeName, entryProps) =>
      val encName = URLEncoder.encode(typeName, "utf-8")
      val coordinatorSingletonManagerName = encName + "Coordinator"
      val coordinatorPath = (self.path / coordinatorSingletonManagerName / "singleton" / "coordinator").toStringWithoutAddress
      var coordinatorRef: Option[ActorRef] = None
      val serviceRegion = context.child(encName).getOrElse {
        if (context.child(coordinatorSingletonManagerName).isEmpty) {
          val coordinatorProps = SeviceCoordinator.props(handOffTimeout = HandOffTimeout,
            snapshotInterval = SnapshotInterval)

          val singletonProps = SeviceCoordinatorSupervisor.props(CoordinatorFailureBackoff, coordinatorProps)
          coordinatorRef = Some(context.actorOf(ClusterSingletonManager.props(
            singletonProps,
            terminationMessage = PoisonPill,
            settings = ClusterSingletonManagerSettings(context.system)
              .withSingletonName("singleton").withRole(Role)),
            name = coordinatorSingletonManagerName))
        } else {
          coordinatorRef = context.child(coordinatorSingletonManagerName)
        }
        context.actorOf(ServiceRegion.props(
          entryProps = entryProps,
          role = Role,
          serviceSize = serviceSize,
          RetryInterval = RetryInterval,
          coordinatorRef = coordinatorRef.get,
          coordinatorPath = coordinatorPath),
          name = encName)
      }
      sender() ! Started(serviceRegion)
    case _ =>
  }
}

object ServiceRegion {
  def props(
    entryProps: Option[Props],
    role: Option[String],
    serviceSize: Int,
    coordinatorRef: ActorRef,
    RetryInterval: FiniteDuration,
    coordinatorPath: String): Props =
    Props(classOf[ServiceRegion], entryProps, role, serviceSize, coordinatorRef, RetryInterval, coordinatorPath)

  case object RetryTick
}

class ServiceRegion(entryProps: Option[Props],
                    role: Option[String],
                    serviceSize: Int,
                    coordinatorRef: ActorRef,
                    RetryInterval: FiniteDuration,
                    coordinatorPath: String) extends Actor with ActorLogging {

  import context.dispatcher

  import SeviceCoordinator._
  import ServiceRegion._
  val cluster = Cluster(context.system)

  val retryTask = context.system.scheduler.schedule(1 seconds, RetryInterval, self, RetryTick)

  //当前空闲的Service
  val freeServiceBuffer = Set.empty[ActorRef]
  //当前繁忙的Service
  val busyServiceBuffer = Set.empty[ActorRef]

  val passivatingBuffers = Set.empty[(Any, ActorRef)]
  var isRegistedOnStart = false
  var isBusySend = false
  var isServiceStarted = false

  val ageOrdering = Ordering.fromLessThan[Member] { (a, b) ⇒ a.isOlderThan(b) }
  var membersByAge: scala.collection.immutable.SortedSet[Member] = scala.collection.immutable.SortedSet.empty(ageOrdering)

  def coordinatorSelection: Option[ActorSelection] =
    membersByAge.headOption.map(m ⇒ context.actorSelection(RootActorPath(m.address) + coordinatorPath))

  var coordinator: Option[ActorRef] = None

  override def preStart() {
    super.preStart()
    cluster.subscribe(self, classOf[MemberEvent])
  }

  override def postStop(): Unit = {
    super.postStop()
    cluster.unsubscribe(self)
    retryTask.cancel()
  }

  def matchingRole(member: Member): Boolean = role match {
    case None    => true
    case Some(r) => member.hasRole(r)
  }

  def changeMembers(newMembers: scala.collection.immutable.SortedSet[Member]): Unit = {
    val before = membersByAge.headOption
    val after = newMembers.headOption
    membersByAge = newMembers
    if (before != after) {
      if (!isServiceStarted) {
        isServiceStarted = true
        if (entryProps.isDefined) {
          if (serviceSize > 0) {
            for (i <- 1 to serviceSize) {
              context.actorOf(entryProps.get, "service" + i)
            }

          }
        }
      }
    }
  }

  def receiveClusterState(state: CurrentClusterState): Unit = {
    changeMembers(scala.collection.immutable.SortedSet.empty(ageOrdering) ++ state.members.filter(m ⇒
      m.status == MemberStatus.Up && matchingRole(m)))
  }

  def receiveClusterEvent(evt: ClusterDomainEvent): Unit = evt match {
    case MemberUp(m) =>
      if (matchingRole(m))
        changeMembers(membersByAge + m)
    case MemberRemoved(m, _) =>
      if (m.uniqueAddress == cluster.selfUniqueAddress)
        context.stop(self)
      else if (matchingRole(m))
        changeMembers(membersByAge - m)
    case _ => unhandled(evt)
  }

  def receive = {
    case evt: ClusterDomainEvent    => receiveClusterEvent(evt)
    case state: CurrentClusterState => receiveClusterState(state)
    case Register(ref) =>
      context.watch(ref)
      freeServiceBuffer += ref
      if (!isRegistedOnStart) {
        isRegistedOnStart = true
      }
      sender ! RegisterAck(self)
    case ServiceFree(ref) =>
      if (busyServiceBuffer.contains(ref)) {
        busyServiceBuffer -= ref
      }
      val lastOption = passivatingBuffers.lastOption
      if (lastOption.isDefined) {
        passivatingBuffers -= lastOption.get
        ref.tell(lastOption.get._1, lastOption.get._2)
        busyServiceBuffer += ref
      } else {
        freeServiceBuffer += ref
      }

    case Terminated(ref) =>
      if (freeServiceBuffer.contains(ref)) {
        freeServiceBuffer -= ref
      } else if (busyServiceBuffer.contains(ref)) {
        busyServiceBuffer -= ref
      }
    case ServiceRef(ref) =>
      if (ref.isDefined) {
        val lastOption = passivatingBuffers.lastOption
        if (lastOption.isDefined) {
          passivatingBuffers -= lastOption.get
          ref.get.tell(lastOption.get._1, lastOption.get._2)
        }
      }
    case FreeAck(ref) =>
      coordinator = Some(ref)
    case RetryTick =>
      if (coordinator.isDefined) {
        if (passivatingBuffers.size > 0 && freeServiceBuffer.size == 0) {
          coordinator.get ! GetServiceRef()
        }
        if (isBusySend && freeServiceBuffer.size > 0) {
          coordinator.get ! ServiceFree(self)
          isBusySend = false
        } else if (!isBusySend && freeServiceBuffer.size == 0) {
          isBusySend = true
          coordinator.get ! ServiceBusy(self)
        }
      } else {
        register()
      }

    case msg =>
      if (freeServiceBuffer.size > 0) {
        val ref = freeServiceBuffer.last
        freeServiceBuffer -= ref
        busyServiceBuffer += ref
        ref forward msg
        //当前service比较忙，暂时不再接收其他节点发送的消息
        if (coordinator.isDefined) {
          if (!isBusySend && freeServiceBuffer.size == 0) {
            isBusySend = true
            coordinator.get ! ServiceBusy(self)
          }
        }
      } else {
        passivatingBuffers += ((msg, sender))
        if (coordinator.isDefined) {
          coordinator.get ! GetServiceRef()
        }
      }
  }

  def register(): Unit = {
    coordinatorSelection.foreach(_ ! ServiceFree(self))
  }

}

/**
 * service类必须实现AbdtractServiceActor
 */
trait AbdtractServiceActor extends Actor with ActorLogging {

  import context.dispatcher
  import SeviceCoordinator._

  val testTask = context.system.scheduler.schedule(2 seconds, 2 seconds, self, "test")
  var isRegisted = false

  override def preStart() {
    super.preStart()
    context.parent ! Register(self)
  }

  override def aroundReceive(receive: Actor.Receive, msg: Any): Unit = {
    msg match {
      case "test" =>
        if (isRegisted) {
          testTask.cancel()
        } else {
          this.context.parent ! ServiceFree(self)
        }
      case RegisterAck(coordinator) =>
        log.info("service register success")
        isRegisted = true
      case x =>
        log.info("receive service event[{}]", x)
        super.aroundReceive(receive, msg)
        this.context.parent ! ServiceFree(self)
    }

  }
}

class SeviceActor extends Actor with ActorLogging {

  def receive = {
    case _ =>
  }
}

private case object SnapshotTick

object SeviceCoordinatorSupervisor {
  /**
   * Factory method for the [[akka.actor.Props]] of the [[ShardCoordinator]] actor.
   */
  def props(failureBackoff: FiniteDuration, coordinatorProps: Props): Props =
    Props(classOf[SeviceCoordinatorSupervisor], failureBackoff, coordinatorProps)

  /**
   * INTERNAL API
   */
  private case object StartCoordinator
}

class SeviceCoordinatorSupervisor(failureBackoff: FiniteDuration, coordinatorProps: Props) extends Actor with ActorLogging {
  import SeviceCoordinatorSupervisor._

  var coordinator: ActorRef = null

  def startCoordinator(): Unit = {
    coordinator = context.actorOf(coordinatorProps, "coordinator")
    context.watch(coordinator)
  }

  override def preStart(): Unit = {
    startCoordinator()
    log.info(self.path.toStringWithoutAddress)
  }

  def receive = {
    case Terminated(_) =>
      import context.dispatcher
      context.system.scheduler.scheduleOnce(failureBackoff, self, StartCoordinator)
    case StartCoordinator => startCoordinator()
    case x =>
      coordinator forward x
  }
}

object SeviceCoordinator {

  sealed trait DomainEvent
  sealed trait CoordinatorCommand
  sealed trait CoordinatorMessage

  @SerialVersionUID(1L) case class Register(service: ActorRef) extends CoordinatorCommand
  @SerialVersionUID(1L) case class RegisterAck(coordinator: ActorRef) extends CoordinatorMessage
  @SerialVersionUID(1L) case class GetServiceRef() extends CoordinatorCommand
  @SerialVersionUID(1L) case class ServiceRef(ref: Option[ActorRef]) extends CoordinatorMessage

  @SerialVersionUID(1L) case class ServiceFree(service: ActorRef) extends DomainEvent

  @SerialVersionUID(1L) case class ServiceBusy(service: ActorRef) extends DomainEvent

  @SerialVersionUID(1L) case class FreeAck(coordinator: ActorRef) extends CoordinatorMessage

  private case object AfterRecover
  object State {
    val empty = State()
  }

  @SerialVersionUID(1L) case class State private (
      val services: Set[ActorRef] = Set.empty[ActorRef]) {

    def updated(event: DomainEvent): State = event match {
      case ServiceFree(service) =>
        copy(services = { services.update(service, true); services.clone() })
      case ServiceBusy(service: ActorRef) =>
        copy(services = { services.update(service, false); services.clone() })
    }
  }

  def props(handOffTimeout: FiniteDuration, snapshotInterval: FiniteDuration): Props =
    Props(classOf[SeviceCoordinator], handOffTimeout, snapshotInterval)
}

class SeviceCoordinator(handOffTimeout: FiniteDuration, snapshotInterval: FiniteDuration) extends PersistentActor with ActorLogging {

  import context.dispatcher
  import SeviceCoordinator._

  override def persistenceId = self.path.toStringWithoutAddress

  var persistentState = State.empty

  val snapshotTask = context.system.scheduler.schedule(snapshotInterval, snapshotInterval, self, SnapshotTick)

  Cluster(context.system).subscribe(self, ClusterShuttingDown.getClass)
  self ! AfterRecover

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, state: State) =>
      log.debug("receiveRecover SnapshotOffer {}", state)
      persistentState = state

    case evt: DomainEvent =>
      evt match {
        case ServiceFree(service) =>
          persistentState = persistentState.updated(evt)
          sender ! FreeAck(self)
        case ServiceBusy(service: ActorRef) =>
          persistentState = persistentState.updated(evt)
      }
    case GetServiceRef() =>
      sender ! ServiceRef(persistentState.services.headOption)
    case _ =>
  }

  override def receiveCommand: Receive = {
    case SnapshotTick =>
      log.debug("Saving persistent snapshot")
      saveSnapshot(persistentState)
    case GetServiceRef() =>
      sender ! ServiceRef(persistentState.services.headOption)
    case ClusterShuttingDown ⇒
      context.become(shuttingDown)
    case AfterRecover ⇒
      persistentState.services.foreach(context.watch)
    case evt @ ServiceFree(service) =>
      persistentState = persistentState.updated(evt)
      sender ! FreeAck(self)
    case evt @ ServiceBusy(service: ActorRef) =>
      persistentState = persistentState.updated(evt)
    case _ =>
  }

  def shuttingDown: Receive = {
    case _ =>
  }

}

