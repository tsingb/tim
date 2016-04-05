package com.tsingb.tim.cluster

import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.ActorPath
import akka.actor.ExtendedActorSystem
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Extension
import akka.cluster.client.ClusterClient
import akka.cluster.client.ClusterClientSettings

object ShardClusterClient extends ExtensionId[ShardClusterClientExt] with ExtensionIdProvider {
  override def get(system: ActorSystem) = super.get(system)
  override def lookup = ShardClusterClient
  override def createExtension(system: ExtendedActorSystem) = new ShardClusterClientExt(system)
}

class ShardClusterClientExt(system: ExtendedActorSystem) extends Extension with Serializable {
  lazy val serviceReceivePath = "/user/" + system.settings.config.getString("com.tsingb.tim.service.path")

  lazy val shardMediator = {
    import scala.collection.JavaConversions._
    val initialContacts = scala.collection.mutable.Set.empty[ActorPath]
    system.settings.config.getStringList("akka.actor.initialContact").toSet[String].foreach { path: String =>
      initialContacts += ActorPath.fromString(path)
    }
    val settings = ClusterClientSettings(system)
      .withInitialContacts(initialContacts)
    system.actorOf(akka.cluster.client.ClusterClient.props(settings))
  }

  /**
   * 包装需要发送的集群消息
   */
  def serviceSend(msg: Any): akka.cluster.client.ClusterClient.Send = {
    akka.cluster.client.ClusterClient.Send(serviceReceivePath, msg, localAffinity = false)
  }

  /**
   * 发送集群消息
   */
  def send(msg: Any, ref: ActorRef) {
    shardMediator.tell(serviceSend(msg), ref)
  }

}