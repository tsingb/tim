package com.tsingb.tim.shard

import akka.util.ByteStringBuilder
import com.google.gson.GsonBuilder
import com.tsingb.tim.util.GZip

trait SnapshotData {

  val gsonBuilder = new GsonBuilder()
  //  gsonBuilder.registerTypeAdapter(classOf[scala.collection.mutable.Set[Long]], new SetInstanceCreator());
  val gson = gsonBuilder.create();

  def toByte(): Array[Byte] = {
    val json = toJson()
    val bytes = json.getBytes("UTF-8")
    GZip.gzip(bytes)
  }

  def fromByte(bytes: Array[Byte]) {
    val unZipBytes = GZip.unGZip(bytes)
    val buffer = new ByteStringBuilder()
    buffer.putBytes(unZipBytes)
    val json = buffer.result().utf8String
    fromJson(json)
  }

  def toJson(): String

  def fromJson(jsonStr: String)

}