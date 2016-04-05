package com.tsingb.tim.shard.user

import akka.actor.ActorRef
import akka.actor.Actor
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.tsingb.tim.shard.SnapshotData

/**
 * 连接数据
 */
case class ConnectionRef(var ref: ActorRef, var address: String)

case class UserGroup(groupId: String, var isBlock: Boolean = false)

case class SnapshotMessage(msgId: String, from: String, to: String, modal: Int, time: Long)

case class UserSnapshotData(userName: String) extends SnapshotData {

  var userId: String = ""
  var password: String = ""
  //当前登录客户端
  var device: String = ""

  //客户端token
  var deviceToken: String = ""

  //当前在线协议Actor引用
  var connectionRef: ConnectionRef = ConnectionRef(Actor.noSender, "")

  //当前在线状态
  var isOnline: Boolean = false

  var nickName: String = ""
  var headImage: String = ""
  var createTime: Long = System.currentTimeMillis()
  var modifyTime: Long = System.currentTimeMillis()
  var role: String = "user"
  var token: String = ""
  var invitation: Boolean = false
  var status = 0 // 0:未创建 1：正常  2：已删除
  var banned: Boolean = false
  var bannedTime: Long = 0
  var startBannedTime: Long = -1l
  var endBannedTime: Long = -1l
  var contacts = scala.collection.mutable.Set.empty[String]
  var blocks = scala.collection.mutable.Set.empty[String]
  var groups = scala.collection.mutable.Map.empty[String, UserGroup]
  var receiveGroupMessages = scala.collection.mutable.Map.empty[String, String]
  var pushGroupMessages = scala.collection.mutable.Map.empty[String, String]
  var receiveEvents = scala.collection.mutable.Set.empty[String]
  var pushEvents = scala.collection.mutable.Set.empty[String]
  //接收到为推送的消息
  var receiveMessages = scala.collection.mutable.Map.empty[String, SnapshotMessage]
  //已推送未收到回执的数据
  var pushMessages = scala.collection.mutable.Map.empty[String, SnapshotMessage]
  //已接收未收到已读回执的消息
  var waitDelMessages = scala.collection.mutable.Map.empty[String, SnapshotMessage]
  //已发送，等待回馈的消息
  var sendAndWaitMessage = scala.collection.mutable.Map.empty[String, SnapshotMessage]

  override def toJson(): String = {
    val json = new JsonObject()
    json.addProperty("userName", userName)
    json.addProperty("userId", userId)
    json.addProperty("pwd", password)
    json.addProperty("device", device)
    json.addProperty("deviceToken", deviceToken)
    json.addProperty("isOnline", isOnline)
    json.addProperty("nickName", nickName)
    json.addProperty("headImage", headImage)
    json.addProperty("createTime", createTime)
    json.addProperty("modifyTime", modifyTime)
    json.addProperty("status", status)
    json.addProperty("invitation", invitation)
    json.addProperty("token", token)
    json.addProperty("banned_time", bannedTime)
    json.addProperty("start_banned_time", startBannedTime)
    json.addProperty("end_banned_time", endBannedTime)
    json.addProperty("address", connectionRef.address)
    val contactsJson = new JsonArray()
    contacts.foreach { username => contactsJson.add(username) }
    json.add("contacts", contactsJson)
    val blocksJson = new JsonArray()
    blocks.foreach { username => blocksJson.add(username) }
    json.add("blocks", blocksJson)
    val groupsJson = new JsonArray()
    groups.foreach(e => {
      val (groupId, group) = e
      val groupJson = new JsonObject()
      groupJson.addProperty("group_id", group.groupId)
      groupJson.addProperty("is_block", group.isBlock)
      groupsJson.add(groupJson)
    })
    json.add("groups", groupsJson)
    val eventsJson = new JsonArray()
    receiveEvents.foreach { eventId => eventsJson.add(eventId) }
    json.add("events", eventsJson)
    val messagesJson = new JsonArray()
    receiveGroupMessages.foreach(e => {
      val (msgId, groupId) = e
      val msgJson = new JsonObject()
      msgJson.addProperty("msg_id", msgId)
      msgJson.addProperty("group_id", groupId)
      messagesJson.add(msgJson)
    })
    json.add("messages", messagesJson)
    json.toString()
  }

  override def fromJson(str: String) {
    val json = gson.fromJson(str, classOf[JsonObject])
    userId = json.get("userId").getAsString
    device = json.get("device").getAsString
    password = json.get("pwd").getAsString
    deviceToken = json.get("deviceToken").getAsString
    isOnline = json.get("isOnline").getAsBoolean
    nickName = json.get("nickName").getAsString
    headImage = json.get("headImage").getAsString
    createTime = json.get("createTime").getAsLong
    modifyTime = json.get("modifyTime").getAsLong
    status = json.get("status").getAsInt
    token = json.get("token").getAsString
    invitation = json.get("invitation").getAsBoolean

    if (json.has("contacts")) {
      val contactsJson = json.get("contacts").getAsJsonArray
      val it = contactsJson.iterator()
      while (it.hasNext()) {
        val username = it.next().getAsString
        contacts += username
      }
    }

    if (json.has("blocks")) {
      val blocksJson = json.get("blocks").getAsJsonArray
      val it = blocksJson.iterator()
      while (it.hasNext()) {
        val username = it.next().getAsString
        blocks += username
      }
    }

    if (json.has("events")) {
      val eventsJson = json.get("events").getAsJsonArray
      val it = eventsJson.iterator()
      while (it.hasNext()) {
        val evtId = it.next().getAsString
        receiveEvents += evtId
      }
    }

    if (json.has("messages")) {
      val messagesJson = json.get("messages").getAsJsonArray
      val it = messagesJson.iterator()
      while (it.hasNext()) {
        val msg = it.next().getAsJsonObject
        receiveGroupMessages += (msg.get("msg_id").getAsString -> msg.get("group_id").getAsString)
      }
    }

    if (json.has("groups")) {
      val groupsJson = json.get("groups").getAsJsonArray
      val it = groupsJson.iterator()
      while (it.hasNext()) {
        val group = it.next().getAsJsonObject
        groups += (group.get("group_id").getAsString -> UserGroup(group.get("group_id").getAsString, group.get("is_block").getAsBoolean))
      }
    }

  }
}