package com.tsingb.tim.cluster

import java.net.URLEncoder
import scala.concurrent.Await
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS
import com.tsingb.tim.cluster.util.RedisHelper
import ClusterCoordinator.ClusterNode
import ClusterCoordinator.Register
import ClusterCoordinator.RegisterAck
import ClusterGuardian.Start
import ClusterGuardian.Started
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.ActorSelection.toScala
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.NoSerializationVerificationNeeded
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.pattern.ask
import com.tsingb.tim.cluster.serializer.ClusterSerializable
/**
 * 服务注册
 * 集群自动移除异常节点，节点恢复后需重新注册
 * alias:节点别名，方便查询每个节点的负载
 * protocol:http、socket、websocket
 * host:外网地址
 * port：外网端口
 *
 */
object ClusterExtension extends ExtensionId[ClusterExt] with ExtensionIdProvider {
  override def get(system: ActorSystem) = super.get(system)
  override def lookup = ClusterExtension
  override def createExtension(system: ExtendedActorSystem) = new ClusterExt(system)

  case class GetClusterNodes(protocol: String) extends ClusterSerializable

  case class RemoveClusterNode() extends ClusterSerializable

  case class IncreaseClusterNodeLoad() extends ClusterSerializable

  case class DecreaseClusterNodeLoad() extends ClusterSerializable
}

class ClusterExt(system: ExtendedActorSystem) extends Extension with Serializable {

  val cluster = Cluster(system)

  private[cluster] object Settings {
    val config = system.settings.config.getConfig("com.tsingb.tim.cluster")
    val Role: Option[String] = config.getString("role") match {
      case "" => None
      case r  => Some(r)
    }
    val MaxLoad: Int = config.getInt("max-load")
    val AlertLoad: Int = config.getInt("alert-load")
    val HasNecessaryClusterRole: Boolean = Role.forall(cluster.selfRoles.contains)
    val GuardianName: String = config.getString("guardian-name")
    val SnapshotInterval: FiniteDuration = config.getDuration("snapshot-interval", MILLISECONDS).millis
    val RetryInterval: FiniteDuration = config.getDuration("retry-interval", MILLISECONDS).millis
    val HandOffTimeout: FiniteDuration = config.getDuration("handoff-timeout", MILLISECONDS).millis
    val CoordinatorFailureBackoff = config.getDuration("coordinator-failure-backoff", MILLISECONDS).millis
    val RegisteInterval: FiniteDuration = config.getDuration("registe-interval", MILLISECONDS).millis
  }

  import Settings._
  import ClusterGuardian._

  private var clusterRegion: Option[ActorRef] = None

  private lazy val guardian = system.actorOf(Props[ClusterGuardian], Settings.GuardianName)

  def start(alias: String,
            clusterName: String,
            role: Option[String],
            protocol: String,
            host: String,
            port: Int): ActorRef = {
    implicit val timeout = system.settings.CreationTimeout
    val startMsg = Start(alias, clusterName, protocol, host, port)
    val Started(region) = Await.result(guardian ? startMsg, timeout.duration)
    clusterRegion = Some(region)
    region
  }

  def clusterRef(): ActorRef = if (clusterRegion.isDefined) {
    clusterRegion.get
  } else {
    throw new IllegalArgumentException("cluster not registe")
  }

}

private[cluster] object ClusterGuardian {
  case class Start(alias: String, clusterName: String, protocol: String, host: String, port: Int)
    extends NoSerializationVerificationNeeded

  case class Started(clusterRegion: ActorRef) extends NoSerializationVerificationNeeded
}

private[cluster] class ClusterGuardian extends Actor with ActorLogging {

  import ClusterGuardian._

  val NodeCluster = ClusterExtension(context.system)
  val cluster = Cluster(context.system)

  import NodeCluster.Settings._

  def receive = {
    case Start(alias, clusterName, protocol, host, port) =>
      val encName = URLEncoder.encode(clusterName, "utf-8")
      val coordinatorSingletonManagerName = encName + "Coordinator"
      val coordinatorPath = (self.path / coordinatorSingletonManagerName / "singleton" / "coordinator").toStringWithoutAddress
      var coordinatorRef: Option[ActorRef] = None
      val clusterRegion = context.child(encName).getOrElse {
        if (context.child(coordinatorSingletonManagerName).isEmpty) {
          val coordinatorProps = ClusterCoordinator.props(handOffTimeout = HandOffTimeout,
            snapshotInterval = SnapshotInterval)

          val singletonProps = ClusterCoordinatorSupervisor.props(CoordinatorFailureBackoff, coordinatorProps)
          coordinatorRef = Some(context.actorOf(ClusterSingletonManager.props(
            singletonProps,
            terminationMessage = PoisonPill,
            settings = ClusterSingletonManagerSettings(context.system)
              .withSingletonName("singleton").withRole(Role)),
            name = coordinatorSingletonManagerName))
        } else {
          coordinatorRef = context.child(coordinatorSingletonManagerName)
        }
        context.actorOf(ClusterRegion.props(
          alias = alias,
          clusterName = clusterName,
          role = Role,
          protocol = protocol,
          host = host,
          port = port,
          coordinatorRef = coordinatorRef.get,
          coordinatorPath = coordinatorPath,
          registeInterval = RegisteInterval),
          name = encName)
      }
      sender() ! Started(clusterRegion)
    case x =>
  }

}

object ClusterRegion {
  def props(
    alias: String,
    clusterName: String,
    role: Option[String],
    protocol: String,
    host: String,
    port: Int,
    coordinatorRef: ActorRef,
    coordinatorPath: String,
    registeInterval: FiniteDuration): Props =
    Props(classOf[ClusterRegion], alias, clusterName, role, protocol, host, port, coordinatorRef, coordinatorPath, registeInterval)

  case object RegistetryTick
}

class ClusterRegion(alias: String, clusterName: String, role: Option[String], protocol: String, host: String, port: Int, coordinatorRef: ActorRef, coordinatorPath: String, registeInterval: FiniteDuration) extends Actor with ActorLogging {

  val cluster = Cluster(context.system)
  import ClusterCoordinator._
  import ClusterRegion._
  import ClusterExtension._
  import context.dispatcher

  val passivatingBuffers = scala.collection.mutable.Set.empty[(Any, ActorRef)]
  val ageOrdering = Ordering.fromLessThan[Member] { (a, b) ⇒ a.isOlderThan(b) }
  var membersByAge: scala.collection.immutable.SortedSet[Member] = scala.collection.immutable.SortedSet.empty(ageOrdering)

  def coordinatorSelection: Option[ActorSelection] =
    membersByAge.headOption.map(m => context.actorSelection(RootActorPath(m.address) + coordinatorPath))

  var coordinator: Option[ActorRef] = None

  override def preStart() {
    super.preStart()
    cluster.subscribe(self, classOf[MemberEvent])
    context.system.scheduler.scheduleOnce(registeInterval, self, RegistetryTick)
  }

  override def postStop(): Unit = {
    super.postStop()
    cluster.unsubscribe(self)
  }

  def matchingRole(member: Member): Boolean = role match {
    case None    => true
    case Some(r) => member.hasRole(r)
  }

  def changeMembers(newMembers: scala.collection.immutable.SortedSet[Member]): Unit = {
    val before = membersByAge.headOption
    val after = newMembers.headOption
    membersByAge = newMembers
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
    case RegisterAck(ref) =>
      coordinator = Some(ref)
      passivatingBuffers.foreach { e =>
        val (obj, senderRef) = e
        coordinator.get ! obj
      }
      passivatingBuffers.clear()
    case RegistetryTick =>
      register()
    case obj @ IncreaseClusterNodeLoad() =>
      if (coordinator.isDefined) {
        coordinator.get ! obj
      } else {
        passivatingBuffers += ((obj, sender))
        register()
      }
    case obj @ DecreaseClusterNodeLoad() =>
      if (coordinator.isDefined) {
        coordinator.get ! obj
      } else {
        passivatingBuffers += ((obj, sender))
        register()
      }
    case obj @ RemoveClusterNode() =>
      if (coordinator.isDefined) {
        coordinator.get ! obj
      } else {
        passivatingBuffers += ((obj, sender))
        register()
      }
    case x =>
  }

  def register(): Unit = {
    coordinatorSelection.foreach(_ ! Register(alias, clusterName, protocol, host, port))
  }

}

private[cluster] object ClusterCoordinatorSupervisor {
  def props(failureBackoff: FiniteDuration, coordinatorProps: Props): Props =
    Props(classOf[ClusterCoordinatorSupervisor], failureBackoff, coordinatorProps)

  private case object StartCoordinator
}

private[cluster] class ClusterCoordinatorSupervisor(failureBackoff: FiniteDuration, coordinatorProps: Props) extends Actor with ActorLogging {

  import ClusterCoordinatorSupervisor._
  var coordinator: ActorRef = null

  def startCoordinator(): Unit = {
    coordinator = context.actorOf(coordinatorProps, "coordinator")
    context.watch(coordinator)
  }

  override def preStart(): Unit = {
    startCoordinator()
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

private[cluster] object ClusterCoordinator {

  sealed trait ClusterEvent extends ClusterSerializable

  case class Register(alias: String, clusterName: String, protocol: String, host: String, port: Int) extends ClusterEvent

  case class RegisterAck(coordinator: ActorRef) extends ClusterEvent

  case class ClusterNode(alias: String, clusterName: String, protocol: String, host: String, port: Int, var load: Int) extends ClusterSerializable

  def props(handOffTimeout: FiniteDuration, snapshotInterval: FiniteDuration): Props =
    Props(classOf[ClusterCoordinator], handOffTimeout, snapshotInterval)
}

private[cluster] class ClusterCoordinator(handOffTimeout: FiniteDuration, snapshotInterval: FiniteDuration) extends Actor with ActorLogging {
  import ClusterCoordinator._
  import ClusterExtension._

  val map = scala.collection.mutable.Map.empty[ActorRef, ClusterNode]

  def makeKey(set: Set[String]): String = {
    set.mkString("::")
  }

  def makeMember(clusterName: String, protocol: String, host: String, port: Int): String = {
    val sf = new StringBuilder()
    sf.append(protocol)
    sf.append("://")
    sf.append(clusterName)
    sf.append("@")
    sf.append(host)
    sf.append(":")
    sf.append(port)
    sf.toString()
  }

  def receive = {
    case Register(alias, clusterName, protocol, host, port) =>
      val ref = sender
      if (!map.contains(ref)) {
        val node = ClusterNode(alias, clusterName, protocol, host, port, 0)
        map += ref -> node
      }
      ref ! RegisterAck(self)
      RedisHelper.zadd(makeKey(Set("cluster", protocol, "node")), makeMember(clusterName, protocol, host, port), 0)
    case GetClusterNodes(protocol) =>
      
    case IncreaseClusterNodeLoad() =>
      val ref = sender
      if (map.contains(ref)) {
        val node = map(ref)
        node.load += 1
        RedisHelper.zincrby(makeKey(Set("cluster", node.clusterName, node.protocol, "node")), makeMember(node.clusterName, node.protocol, node.host, node.port), 1)
      }
    case DecreaseClusterNodeLoad() =>
      val ref = sender
      if (map.contains(ref)) {
        val node = map(ref)
        node.load -= 1
        RedisHelper.zincrby(makeKey(Set("cluster", node.clusterName, node.protocol, "node")), makeMember(node.clusterName, node.protocol, node.host, node.port), -1)
      }
    case RemoveClusterNode() =>
      val ref = sender
      if (map.contains(ref)) {
        val node = map(ref)
        map -= ref
        RedisHelper.zrem(makeKey(Set("cluster", node.clusterName, node.protocol, "node")), makeMember(node.clusterName, node.protocol, node.host, node.port))
      }
    case Terminated(ref) =>
      if (map.contains(ref)) {
        val node = map(ref)
        map -= ref
        RedisHelper.zrem(makeKey(Set("cluster", node.clusterName, node.protocol, "node")), makeMember(node.clusterName, node.protocol, node.host, node.port))
      }
    case x =>
  }
}