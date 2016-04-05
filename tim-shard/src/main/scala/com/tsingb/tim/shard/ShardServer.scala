package com.tsingb.tim.shard

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.ActorLogging
import akka.actor.Actor
import com.tsingb.tim.shard.user.UserShardExtension
import akka.actor.Props
import akka.routing.BalancingPool
import com.tsingb.tim.shard.group.GroupShardExtension
import akka.cluster.client.ClusterClientReceptionist
import com.tsingb.tim.shard.service.MongoService

object ShardServer extends App {

  case object Start
  //默认从启动参数回去端口号
  if (!args.isEmpty) System.setProperty("akka.remote.netty.tcp.port", args(0))
  val config = ConfigFactory.load("application.conf")
  implicit val system = ActorSystem("tim-shard", config)
  var shardServer = system.actorOf(Props(classOf[ShardServer]), "tim-shard")
  shardServer ! Start
}

class ShardServer extends Actor with ActorLogging {
  def receive = {
    case ShardServer.Start =>
      startServer()
    case x =>
      log.info("[{}]", x)
  }

  def startServer() {
    MongoService(context.system).start
    UserShardExtension(context.system).start
    GroupShardExtension(context.system).start

    //service接待员Actor启动
    val serviceActor = context.actorOf(BalancingPool(30).props(Props[UsherService]), "serviceUsher")
    ClusterClientReceptionist(context.system).registerService(serviceActor)
  }
}