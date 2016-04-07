package com.tsingb.tim.socket.protocol

import com.tsingb.tim.util.GZip._
import com.google.gson.Gson

object ByteSerializer {

  private val gson = new Gson()

  def fromBinary(eventType: Int, data: Array[Byte]): Option[Protocol] = {
    try {
      val json = new String(unGZip(data), "UTF-8")
      fromJSON(eventType, json)
    } catch {
      case t: Throwable =>
        None
    }
  }

  def fromJSON(eventType: Int, str: String): Option[Protocol] = {
    eventType match {
      case 1 =>
        Some(Connect.fromJson(str))
      case 3 =>
        Some(Ping())
      case 4 =>
        Some(Pong())
      case 5 =>
        Some(Msg.fromJSON("chat", str))
      case 6 =>
        Some(Msg.fromJSON("groupchat", str))
      case 7 =>
        Some(Msg.fromJSON("roomchat", str))
      case 8 =>
        Some(MsgAck.fromJSON(str))
      case 10 =>
        Some(EvtAck.fromJSON(str))
      case 11 =>
        Some(Logout())
    }
  }

  def byte2Int(bs: Array[Byte]): Int = {
    val mask = 0xff
    var temp = 0
    var n = 0
    for (i <- 0 to 3) {
      n <<= 8
      temp = bs(i) & mask
      n |= temp
    }
    n
  }

  def int2Byte(integer: Int): Array[Byte] = {
    var value = integer
    if (integer < 0) {
      value = ~integer
    }

    val byteNum = (40 - Integer.numberOfLeadingZeros(value)) / 8
    val bs = new Array[Byte](4)
    for (n <- 0 to byteNum) {
      bs(3 - n) = (integer >>> (n * 8)).toByte
    }
    bs
  }
}