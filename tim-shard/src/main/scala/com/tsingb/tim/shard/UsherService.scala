package com.tsingb.tim.shard

import akka.actor.ActorLogging
import akka.actor.Actor
import com.tsingb.tim.shard.user.UserShardExtension
import akka.cluster.Cluster
import com.tsingb.tim.cmd._
import com.tsingb.tim.event._
import com.tsingb.tim.data._
import com.tsingb.tim.protocol._
import com.tsingb.tim.shard.group.GroupShardExtension

class UsherService extends Actor with ActorLogging {
  val userShard = UserShardExtension(context.system).shardRegion
  val groupShard = GroupShardExtension(context.system).shardRegion

  val cluster = Cluster(context.system)
  val address = cluster.selfAddress

  def receive = {
    case cmd: UserCmd =>
      userShard forward cmd
    case cmd: EventCmd =>
      userShard forward cmd
    case cmd: MessageCmd =>
      userShard forward cmd
    case cmd: GroupCmd =>
      groupShard forward cmd
    case x =>
      log.info("unknow [{}]", x)
  }
}