package com.tsingb.tim.cluster.serializer

import akka.actor.ExtendedActorSystem
import akka.serialization.{ Serialization, Serializer }
import akka.util.{ ByteIterator, ByteStringBuilder, ByteString }
import java.nio.ByteOrder
import scala.collection.mutable
import scala.collection.immutable
import scala.util.Failure
import scala.util.Success
import com.tsingb.tim.cluster.ClusterExtension
import com.tsingb.tim.cluster.ClusterCoordinator
import akka.actor.ActorRef

trait ClusterSerializable

object StringSerializer {
  implicit val byteOrder = ByteOrder.BIG_ENDIAN
  def appendToByteString(builder: ByteStringBuilder, str: String) {
    if (str != null) {
      val bytes = str.getBytes
      builder.putInt(bytes.length)
      builder.putBytes(bytes)
    } else {
      builder.putInt(-1)
    }
  }
  def fromByteIterator(data: ByteIterator): String = {
    val len = data.getInt
    if (len >= 0) {
      val str = Array.ofDim[Byte](len)
      data.getBytes(str)
      new String(str)
    } else {
      null
    }
  }
}

class ClusterSerializer(val system: ExtendedActorSystem) extends Serializer {
  import ClusterExtension._
  import ClusterCoordinator._
  implicit val byteOrder = ByteOrder.BIG_ENDIAN
  final def includeManifest: Boolean = false
  final def identifier: Int = 3000
  final def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case GetClusterNodes(protocol) =>
        val builder = ByteString.newBuilder
        builder.putInt(1)
        StringSerializer.appendToByteString(builder, protocol)
        builder.result.toArray
      case RemoveClusterNode() =>
        val builder = ByteString.newBuilder
        builder.putInt(2)
        builder.result.toArray
      case IncreaseClusterNodeLoad() =>
        val builder = ByteString.newBuilder
        builder.putInt(3)
        builder.result.toArray
      case DecreaseClusterNodeLoad() =>
        val builder = ByteString.newBuilder
        builder.putInt(4)
        builder.result.toArray
      case Register(alias, clusterName, protocol, host, port) =>
        val builder = ByteString.newBuilder
        builder.putInt(5)
        StringSerializer.appendToByteString(builder, alias)
        StringSerializer.appendToByteString(builder, clusterName)
        StringSerializer.appendToByteString(builder, protocol)
        StringSerializer.appendToByteString(builder, host)
        builder.putInt(port)
        builder.result.toArray
      case RegisterAck(coordinator) =>
        val builder = ByteString.newBuilder
        builder.putInt(6)
        StringSerializer.appendToByteString(builder, Serialization.serializedActorPath(coordinator))
        builder.result.toArray
      case ClusterNode(alias, clusterName, protocol, host, port, load) =>
        val builder = ByteString.newBuilder
        builder.putInt(7)
        StringSerializer.appendToByteString(builder, alias)
        StringSerializer.appendToByteString(builder, clusterName)
        StringSerializer.appendToByteString(builder, protocol)
        StringSerializer.appendToByteString(builder, host)
        builder.putInt(port)
        builder.putInt(load)
        builder.result.toArray
      case _ => Array[Byte]()
    }
  }
  final def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val data = ByteString(bytes).iterator
    val code = data.getInt
    code match {
      case 1 =>
        val protocol = StringSerializer.fromByteIterator(data)
        GetClusterNodes(protocol)
      case 2 =>
        RemoveClusterNode()
      case 3 =>
        IncreaseClusterNodeLoad()
      case 4 =>
        DecreaseClusterNodeLoad()
      case 5 =>
        val alias = StringSerializer.fromByteIterator(data)
        val clusterName = StringSerializer.fromByteIterator(data)
        val protocol = StringSerializer.fromByteIterator(data)
        val host = StringSerializer.fromByteIterator(data)
        val port = data.getInt
        Register(alias, clusterName, protocol, host, port)
      case 6 =>
        val coordinator = system.provider.resolveActorRef(StringSerializer.fromByteIterator(data))
        RegisterAck(coordinator)
      case 7 =>
        val alias = StringSerializer.fromByteIterator(data)
        val clusterName = StringSerializer.fromByteIterator(data)
        val protocol = StringSerializer.fromByteIterator(data)
        val host = StringSerializer.fromByteIterator(data)
        val port = data.getInt
        val load = data.getInt
        ClusterNode(alias, clusterName, protocol, host, port, load)
    }

  }
}