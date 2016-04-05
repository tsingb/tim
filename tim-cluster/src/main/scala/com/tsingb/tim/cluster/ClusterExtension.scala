package com.tsingb.tim.cluster

import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ActorSystem
import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.cluster.Cluster

object ClusterExtension extends ExtensionId[ClusterExt] with ExtensionIdProvider {
  override def get(system: ActorSystem) = super.get(system)
  override def lookup = ClusterExtension
  override def createExtension(system: ExtendedActorSystem) = new ClusterExt(system)
}

class ClusterExt(system: ExtendedActorSystem) extends Extension with Serializable {

  val cluster = Cluster(system)

  private[cluster] object Settings {
    val config = system.settings.config.getConfig("com.tsingb.tim.cluster")
    val Role: Option[String] = config.getString("role") match {
      case "" => None
      case r  => Some(r)
    }
    val HasNecessaryClusterRole: Boolean = Role.forall(cluster.selfRoles.contains)
    val GuardianName: String = config.getString("guardian-name")
  }

}

private[cluster] class ClusterGuardian extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  def receive = {
    case x =>
  }

}

class ClusterRegion extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  def receive = {
    case x =>
  }

}

private[cluster] class ClusterSupervisor extends Actor with ActorLogging {

  def receive = {
    case x =>
  }

}