package com.tsingb.tim.http

import com.google.gson.JsonObject
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.tsingb.tim.data.`package`.UserData
import com.tsingb.tim.cmd.CodeResultCmd
package object protocol {
  trait HttpProtocol

  case class Admin(admin: String, password: String) extends HttpProtocol

  case class User(username: String, password: String, nickname: String) extends HttpProtocol

  case class Group(groupId: String, groupName: String, owner: String, desc: String, public: Boolean, maxUsers: Int, approval: Boolean) extends HttpProtocol

  case class NickName(nickname: String) extends HttpProtocol

  case class NewPwd(newpassword: String) extends HttpProtocol

  case class BannedUser(time: Long) extends HttpProtocol

  case class CreateUserResult(username: String, code: Int, result: String) extends HttpProtocol

  case class CodeResult(code: Int, msg: String) extends HttpProtocol

  case class Friend(msg: String) extends HttpProtocol

  object JsonProtocol {
    private val gson = new Gson()

    def fromSingleJson[T <: HttpProtocol](json: String, classOfT: Class[T]): T = {
      gson.fromJson(json, classOfT)
    }

    def toJson(str: String): JsonObject = {
      val json = gson.fromJson(str, classOf[JsonObject])
      json
    }

    def toGroup(str: String): Group = {
      val json = gson.fromJson(str, classOf[JsonObject])
      val groupName = json.get("group_name").getAsString
      val owner = json.get("owner").getAsString
      var desc = ""
      if (json.has("desc")) {
        desc = json.get("desc").getAsString
      }
      var public = true
      if (json.has("public")) {
        public = json.get("public").getAsBoolean
      }

      var maxUsers = 200
      if (json.has("max")) {
        maxUsers = json.get("max").getAsInt
      }
      var approval = false
      if (!public) {
        approval = true
      } else if (json.has("approval")) {
        approval = json.get("approval").getAsBoolean
      }
      Group("", groupName, owner, desc, public, maxUsers, approval)

    }

    def fromJson[T <: HttpProtocol](json: String, classOfT: Class[T]): Set[T] = {
      //    try {
      var jsonStr = json.trim()
      if (!jsonStr.startsWith("[")) {
        jsonStr = "[" + json + "]"
      }
      val jsonArray = gson.fromJson(jsonStr, classOf[JsonArray])
      val it = jsonArray.iterator()
      val set = scala.collection.mutable.Set.empty[T]
      while (it.hasNext()) {
        val element = it.next().getAsJsonObject
        set += gson.fromJson(element, classOfT)
      }
      set.toSet
    }

    def toJson(msg: HttpProtocol): String = {
      gson.toJson(msg)
    }

    def toJson(user: UserData): String = {
      gson.toJson(user)
    }

    def toJson(msg: CodeResultCmd): String = {
      gson.toJson(msg)
    }

    def toJson(msg: Any): String = {
      gson.toJson(msg)
    }

    def toJson(msgs: Set[_ <: HttpProtocol]): String = {
      val arr = new JsonArray()
      msgs.foreach { msg => arr.add(gson.toJsonTree(msg)) }
      arr.toString()
    }

  }
}