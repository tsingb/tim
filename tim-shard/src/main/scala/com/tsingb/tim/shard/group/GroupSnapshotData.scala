package com.tsingb.tim.shard.group

import com.tsingb.tim.shard.SnapshotData
import com.google.gson.JsonObject
import com.google.gson.JsonArray

/**
 * 群组成员
 */
case class GroupMember(userName: String, var role: String, var banned: Boolean, var bannedTime: Long,
                       var startBannedTime: Long,
                       var endBannedTime: Long, var joinTime: Long)

case class GroupSnapshotData(groupId: String) extends SnapshotData {

  var groupName: String = ""
  var desc: String = ""
  var owner: String = ""
  var public: Boolean = true //群是否公开
  var maxUsers: Int = 3000
  var createTime: Long = 0l
  var modifyTime: Long = 0l
  var banned: Boolean = false
  var bannedTime: Long = 0
  var startBannedTime: Long = 0
  var endBannedTime: Long = 0
  var blocks = scala.collection.mutable.Set.empty[String]
  var members = scala.collection.mutable.Map.empty[String, GroupMember]
  var approvalMembers = scala.collection.mutable.Map.empty[String, GroupMember]
  var removeMembers = scala.collection.mutable.Map.empty[String, GroupMember]
  var status: Int = 0

  override def toJson(): String = {
    val json = new JsonObject()
    json.addProperty("groupId", groupId)
    json.addProperty("groupName", groupName)
    json.addProperty("desc", desc)
    json.addProperty("owner", owner)
    json.addProperty("public", public)
    json.addProperty("maxUsers", maxUsers)
    json.addProperty("createTime", createTime)
    json.addProperty("modifyTime", modifyTime)
    json.addProperty("banned", banned)
    json.addProperty("bannedTime", bannedTime)
    json.addProperty("startBannedTime", startBannedTime)
    json.addProperty("endBannedTime", endBannedTime)
    json.addProperty("status", status)
    val membersJson = new JsonArray()
    members.foreach(e => {
      val (name, member) = e
      val memberJson = gson.toJson(member)
      membersJson.add(memberJson)
    })
    json.add("members", membersJson)
    val approvalMembersJson = new JsonArray()
    approvalMembers.foreach(e => {
      val (name, member) = e
      val memberJson = gson.toJson(member)
      approvalMembersJson.add(memberJson)
    })
    json.add("approvalMembers", approvalMembersJson)
    json.toString()
  }

  override def fromJson(str: String) {
    val json = gson.fromJson(str, classOf[JsonObject])
    groupName = json.get("groupName").getAsString
    desc = json.get("desc").getAsString
    owner = json.get("owner").getAsString
    public = json.get("public").getAsBoolean
    maxUsers = json.get("maxUsers").getAsInt
    createTime = json.get("createTime").getAsLong
    modifyTime = json.get("modifyTime").getAsLong
    banned = json.get("banned").getAsBoolean
    bannedTime = json.get("bannedTime").getAsInt
    startBannedTime = json.get("startBannedTime").getAsLong
    endBannedTime = json.get("endBannedTime").getAsLong
    status = json.get("status").getAsInt

    val membersJson = json.get("members").getAsJsonArray
    val membersIt = membersJson.iterator()
    members.clear()
    while (membersIt.hasNext()) {
      val elementJson = gson.fromJson(membersIt.next().getAsString, classOf[JsonObject])
      val member = gson.fromJson(elementJson, classOf[GroupMember])
      members += (member.userName -> member)
    }

    val approvalMembersJson = json.get("approvalMembers").getAsJsonArray
    val approvalMembersIt = approvalMembersJson.iterator()
    approvalMembers.clear()
    while (approvalMembersIt.hasNext()) {
      val elementJson = gson.fromJson(approvalMembersIt.next().getAsString, classOf[JsonObject])
      val member = gson.fromJson(elementJson, classOf[GroupMember])
      approvalMembers += (member.userName -> member)
    }

  }

}