package com.tsingb.tim

package object data {

  trait Data extends Serializable

  case class NoData() extends Data

  case class IntData(value: Int) extends Data

  case class LongData(value: Int) extends Data

  case class StringData(value: String) extends Data

  case class UserData(userId: String, userName: String, nickName: String, headImage: String, createTime: Long, modifyTime: Long, activated: Boolean) extends Data

  case class GroupData(groupId: String, groupName: String, owner: String, desc: String, public: Boolean, maxUsers: Int, createTime: Long, modifyTime: Long, activated: Boolean) extends Data

  case class GroupMemberData(userName: String, nickName: String, headImage: String, role: String, joinTime: Long) extends Data

  case class MemberResultData(userName: String, result: Boolean, reason: String) extends Data

  trait Message extends Data {
    def from: String
    def to: String
    def msgId: String
    def body: String
    def ext: String
    def time: Long
  }

  case class UserMessage(from: String, to: String, msgId: String, body: String, ext: String, modal: Int, time: Long) extends Message

  case class GroupMessage(from: String, to: String, msgId: String, body: String, ext: String, time: Long) extends Message

  object Message {

    //普通消息
    object NoAckMsg {
      def unapply(modal: Int): Option[Boolean] = {
        modal match {
          case 0 =>
            Some(true)
          case x =>
            None
        }
      }
    }
    //已收回馈消息
    object AckAfterReceivedMsg {
      def unapply(modal: Int): Option[Boolean] = {
        modal match {
          case 1 =>
            Some(true)
          case x =>
            None
        }
      }
    }
    //已读回馈消息
    object AckAfterReadMsg {
      def unapply(modal: Int): Option[Boolean] = {
        modal match {
          case 2 =>
            Some(true)
          case x =>
            None
        }
      }
    }

    //阅后即焚消息
    object BurnAfterReadMsg {
      def unapply(modal: Int): Option[Boolean] = {
        modal match {
          case 2 =>
            Some(true)
          case x =>
            None
        }
      }
    }

  }
}