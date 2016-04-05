package com.tsingb.tim.shard.group

import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.event.LogSource
import akka.event.Logging
import akka.cluster.sharding.ShardRegion
import akka.actor.ActorSystem
import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.Props
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.actor.ActorRef
import akka.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import scala.concurrent.Future
import akka.util.Timeout
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success
import akka.actor._
import akka.actor.DeadLetterSuppression
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import akka.dispatch.ExecutionContexts
import akka.pattern.{ AskTimeoutException, pipe }
import akka.persistence._
import akka.cluster.sharding.ShardCoordinator._
import org.uncommons.maths.Maths
import com.tsingb.tim.cmd._

/**
 * 群组sharding扩展
 */
object GroupShardExtension extends ExtensionId[GroupShardExt] with ExtensionIdProvider {
  override def get(system: ActorSystem) = super.get(system)
  override def lookup = GroupShardExtension
  override def createExtension(system: ExtendedActorSystem) = new GroupShardExt(system)
  case class AddUserToGroup(userName: String, groupName: String) extends UserCmd
  case class RemoveUserFromGroup(userName: String, groupName: String) extends UserCmd
  case class LeaveFromGrouCmd(userName: String, groupName: String) extends UserCmd
}

class GroupShardExt(system: ExtendedActorSystem) extends Extension with Serializable {

  import ShardRegion.ShardId
  import GroupShardExtension._

  //配置日志
  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

  private[this] val log = Logging(system, this)

  private[this] val shardName = system.settings.config.getString("akka.contrib.cluster.sharding.user-shard-name")

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: UserCmd    => (cmd.userName, cmd)
    case cmd: MessageCmd => (cmd.to, cmd)
    case cmd: EventCmd   => (cmd.to, cmd)
  }

  //最大支持扩展到50个节点
  val numberOfShards = 50

  val extractShardId: ShardRegion.ExtractShardId = {
    case cmd: UserCmd    => getUserShardId(cmd.userName)
    case cmd: MessageCmd => getUserShardId(cmd.to)
    case cmd: EventCmd   => getUserShardId(cmd.to)
  }

  def getUserShardId(userName: String): String = {
    Math.abs((userName.hashCode() % numberOfShards)).toString()
  }

  private val emptyRebalanceResult = Future.successful(Set.empty[ShardId])

  class ConfigurableShardAllocationStrategy(rebalanceThreshold: Int, maxSimultaneousRebalance: Int)
      extends ShardAllocationStrategy with Serializable {

    override def allocateShard(requester: ActorRef, shardId: ShardId,
                               currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]]): Future[ActorRef] = {

      val (regionWithLeastShards, _) = currentShardAllocations.minBy { case (_, v) => v.size }
      Future.successful(regionWithLeastShards)
    }

    override def rebalance(currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]],
                           rebalanceInProgress: Set[ShardId]): Future[Set[ShardId]] = {
      if (rebalanceInProgress.size < maxSimultaneousRebalance) {
        val (regionWithLeastShards, leastShards) = currentShardAllocations.minBy { case (_, v) ⇒ v.size }
        val mostShards = currentShardAllocations.collect {
          case (_, v) => v.filterNot(s => rebalanceInProgress(s))
        }.maxBy(_.size)
        if (mostShards.size - leastShards.size >= rebalanceThreshold)
          Future.successful(Set(mostShards.head))
        else
          emptyRebalanceResult
      } else emptyRebalanceResult
    }
  }

  val settings = akka.cluster.sharding.ClusterShardingSettings(system)

  val allocationStrategy = new ConfigurableShardAllocationStrategy(
    settings.tuningParameters.leastShardAllocationRebalanceThreshold,
    settings.tuningParameters.leastShardAllocationMaxSimultaneousRebalance)

  def start = ClusterSharding(system).start(
    typeName = shardName,
    entityProps = Props[GroupShardActor],
    settings = settings,
    extractEntityId = extractEntityId,
    extractShardId = extractShardId,
    allocationStrategy = allocationStrategy,
    handOffStopMessage = PoisonPill)

  def startProxy = ClusterSharding(system).startProxy(
    shardName,
    Some("sharding-proxy"),
    extractEntityId,
    extractShardId)

  def shardRegion = ClusterSharding(system).shardRegion(shardName)

}