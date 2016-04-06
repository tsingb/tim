package com.tsingb.tim.cluster.util

import redis.clients.jedis.Jedis
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.asScalaSet
import redis.clients.jedis.Tuple

object RedisHelper extends RedisClient {

}

private[util] trait RedisClient {
  val jedis = new Jedis("127.0.0.1", 6379)

  def zadd(key: String, member: String, score: Int) {
    jedis.zadd(key, score, member)
  }

  def zincrby(key: String, member: String, score: Int) {
    jedis.zincrby(key, score, member)
  }

  def zrem(key: String, member: String) {
    jedis.zrem(key, member)
  }

  def zrange(key: String): Map[String, Int] = {
    val set = jedis.zrangeWithScores(key, 0, -1)
    val map = scala.collection.mutable.Map.empty[String, Int]
    set.toSet.foreach { e: Tuple =>
      map += e.getElement -> e.getScore.toInt
    }
    map.toMap
  }

}