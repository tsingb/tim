package com.tsingb.tim.cmd

import com.tsingb.tim.data._

/**
 * 发送消息
 */
case class SendMessageCmd(to: String, msg: Message) extends MessageCmd

/**
 * 转发消息
 */
case class ForwardMessageCmd(to: String, msg: Message) extends MessageCmd

/**
 * 撤回用户消息
 */
case class RetractUserMessageCmd(userName: String, to: String, msgId: String) extends MessageCmd

/**
 * 撤回群组消息
 */
case class RetractGroupMessageCmd(userName: String, to: String, msgId: String) extends MessageCmd

/**
 * 消息以接收
 */
case class MessageReceivedCmd(to: String, msgId: String) extends MessageCmd

/**
 * 消息已读
 */
case class MessageReadCmd(to: String, msgId: String) extends MessageCmd

/**
 * 消息以接收回执
 */
case class MessageReceivedAckCmd(userName: String, msgId: String) extends MessageAckCmd

/**
 * 消息已读回执
 */
case class MessageReadAckCmd(userName: String, msgId: String) extends MessageAckCmd

