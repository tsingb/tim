package com.tsingb.tim.socket.protocol

import com.google.gson.JsonObject
import com.google.gson.Gson

trait Protocol extends Serializable

trait Request extends Protocol
trait Response extends Protocol {
  def toJSON: JsonObject

  override def toString =
    toJSON.toString
}

/**
 * 连接服务器
 */
case class Connect(token: String, device: String, deviceToken: String, sdkVersion: String, time: Long) extends Protocol

object Connect {

  private val gson = new Gson()

  def fromJson(str: String): Connect = {
    val json = gson.fromJson(str, classOf[JsonObject])
    val token = json.get("token").getAsString
    val device = json.get("device").getAsString
    val deviceToken = json.get("device_token").getAsString
    val version = json.get("version").getAsString
    val time = json.get("time").getAsLong
    Connect(token, device, deviceToken, version, time)
  }
}

case class ConnectAck(code: Int, msg: String, time: Long) extends Response {
  override def toJSON: JsonObject = {
    val json = new JsonObject()
    json.addProperty("msg", msg)
    json.addProperty("time", time)
    json
  }
}

case class Ping() extends Request

case class Pong() extends Response {
  override def toJSON: JsonObject = {
    new JsonObject()
  }
}

case class Msg(msgType: String, from: String, to: String, msgId: String, body: String, ext: String, modal: Int, time: Long) extends Request with Response {
  override def toJSON: JsonObject = {
    val json = new JsonObject()
    json.addProperty("type", msgType)
    json.addProperty("from", from)
    json.addProperty("to", to)
    json.addProperty("msg_id", msgId)
    json.addProperty("body", body)
    json.addProperty("ext", ext)
    json.addProperty("modal", modal)
    json.addProperty("time", time)
    json
  }
}

object Msg {
  private val gson = new Gson()
  def fromJSON(msgType: String, str: String): Msg = {
    val json = gson.fromJson(str, classOf[JsonObject])
    val from = json.get("from").getAsString
    val to = json.get("to").getAsString
    val msgId = json.get("msg_id").getAsString
    val body = json.get("body").getAsString
    val ext = json.get("ext").getAsString
    var modal = 0
    if (json.has("modal")) {
      modal = json.get("modal").getAsInt
    }
    val time = json.get("time").getAsLong
    Msg(msgType, from, to, msgId, body, ext, modal, time)
  }

  def fromJSON(msgType: String, json: JsonObject): Msg = {
    val from = json.get("from").getAsString
    val to = json.get("to").getAsString
    val msgId = json.get("msg_id").getAsString
    val body = json.get("body").getAsString
    val ext = json.get("ext").getAsString
    var modal = 0
    if (json.has("modal")) {
      modal = json.get("modal").getAsInt
    }
    val time = json.get("time").getAsLong
    Msg(msgType, from, to, msgId, body, ext, modal, time)
  }
}

case class MsgAck(ackType: Int, msgId: String, time: Long) extends Request with Response {
  override def toJSON: JsonObject = {
    val json = new JsonObject()
    json.addProperty("type", ackType)
    json.addProperty("msg_id", msgId)
    json.addProperty("time", time)
    json
  }
}

object MsgAck {
  private val gson = new Gson()
  def fromJSON(str: String): MsgAck = {
    val json = gson.fromJson(str, classOf[JsonObject])
    val ackType = json.get("type").getAsInt
    val msgId = json.get("msg_id").getAsString
    val time = json.get("time").getAsLong
    MsgAck(ackType, msgId, time)
  }

  def fromJSON(json: JsonObject): MsgAck = {
    val ackType = json.get("type").getAsInt
    val msgId = json.get("msg_id").getAsString
    val time = json.get("time").getAsLong
    MsgAck(ackType, msgId, time)
  }
}

case class Evt(evtType: Int, evtId: String, evt: String, time: Long) extends Request with Response {
  override def toJSON: JsonObject = {
    val json = new JsonObject()
    json.addProperty("type", evtType)
    json.addProperty("evt_id", evtId)
    json.addProperty("evt", evt)
    json.addProperty("time", time)
    json
  }
}

case class EvtAck(evtId: String, time: Long) extends Request with Response {
  override def toJSON: JsonObject = {
    val json = new JsonObject()
    json.addProperty("evt_id", evtId)
    json.addProperty("time", time)
    json
  }
}

object EvtAck {
  private val gson = new Gson()
  def fromJSON(str: String): EvtAck = {
    val json = gson.fromJson(str, classOf[JsonObject])
    val evtId = json.get("evt_id").getAsString
    val time = json.get("time").getAsLong
    EvtAck(evtId, time)
  }

  def fromJSON(json: JsonObject): EvtAck = {
    val evtId = json.get("evt_id").getAsString
    val time = json.get("time").getAsLong
    EvtAck(evtId, time)
  }
}

case class Logout() extends Request



