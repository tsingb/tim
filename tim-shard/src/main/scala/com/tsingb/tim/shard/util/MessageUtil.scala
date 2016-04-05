package com.tsingb.tim.shard.util

import com.typesafe.config.ConfigFactory
import com.google.gson.JsonObject

object MessageUtil {
  private val conf = ConfigFactory.load("message.conf")
  private val msgConf = conf.getConfig("com.tsingb.tim.message")
  private val kicked_from_group = msgConf.getString("kicked_from_group")
  private val destroy_group = msgConf.getString("destroy_group")
  private val join_group = msgConf.getString("join_group")

  /**
   *
   */
  def createGroupMemberKickedMsg(): String = {
    val json = new JsonObject()
    json.addProperty("type", "sys")
    json.addProperty("msg", kicked_from_group)
    json.toString()
  }

}