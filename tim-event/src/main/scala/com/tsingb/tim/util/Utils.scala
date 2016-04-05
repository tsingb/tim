package com.tsingb.tim.util

import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

object TimUtil {

  def nextEventId(): String = {
    UUID.randomUUID().toString()
  }

  def isEmpty(str: String): Boolean = {
    if (str == null || str.trim() == "") {
      true
    } else {
      false
    }
  }
}

object MessageUtil {

}

/**
 * 时间工具类
 */
object TimeUtil {

  val dsf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

  /**
   * 将时间戳转换成日期字符串
   */
  def long2Str(timestamp: Long): String = {
    var date = new Date(timestamp)
    try {
      TimeUtil.dsf.format(date)
    } catch {
      case e: Exception =>
        ""
    }

  }

  /**
   * 当前时间是否在某几个小时时间段内
   */
  def isBetweenHours(begin: Int, end: Int): Boolean = {
    val cal = Calendar.getInstance
    cal.setTime(new Date)
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    if (begin <= hour && end >= hour) {
      true
    } else {
      false
    }

  }

  /**
   * 当前时间戳
   */
  def now2Long(): Long = {
    System.currentTimeMillis()
  }

}