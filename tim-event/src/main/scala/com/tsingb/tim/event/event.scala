package com.tsingb.tim

package object event {

  trait Event extends Serializable {
    def eventId: String
  }

  trait ContactEvent extends Event {
    def friendName: String
  }

  trait GroupEvent extends Event {
    def groupId: String
    def groupName: String
  }

  trait MessageEvent extends Event {
    def msgId: String
  }

  /**
   * 消息已接收
   */
  case class MessageReceivedEvent(msgId: String, from: String, eventId: String) extends MessageEvent

  /**
   * 消息已读
   */
  case class MessageReadEvent(msgId: String, from: String, eventId: String) extends MessageEvent

  /**
   * 好友申请
   */
  case class ContactInvitEvent(friendName: String, reason: String, eventId: String) extends ContactEvent

  /**
   * 好友邀请同意
   */
  case class ContactAgreeEvent(friendName: String, eventId: String) extends ContactEvent

  /**
   * 好友邀请拒绝
   */
  case class ContactRefusedEvent(friendName: String, reason: String, eventId: String) extends ContactEvent

  /**
   * 被好友删除
   */
  case class ContactDeletedEvent(friendName: String, eventId: String) extends ContactEvent

  /**
   * 收到加入群组邀请
   */
  case class GroupInvitReceivedEvent(groupId: String, groupName: String, inviter: String, reason: String, eventId: String) extends GroupEvent

  /**
   * 进群邀请被同意
   */
  case class GroupInvitAccptedEvent(groupId: String, groupName: String, accepter: String, eventId: String) extends GroupEvent

  /**
   * 进群邀请被拒绝
   */
  case class GroupInvitRefusedEvent(groupId: String, groupName: String, decliner: String, reason: String, eventId: String) extends GroupEvent

  /**
   * 群组收到进群申请
   */
  case class GroupApplyReceivedEvent(groupId: String, groupName: String, applyer: String, reason: String, eventId: String) extends GroupEvent

  /**
   * 加群申请被同意
   */
  case class GroupApplyAcceptedEvent(groupId: String, groupName: String, accepter: String, eventId: String) extends GroupEvent

  /**
   * 加群申请被拒绝
   */
  case class GroupApplyRefusedEvent(groupId: String, groupName: String, decliner: String, reason: String, eventId: String) extends GroupEvent

  /**
   * 被管理员踢出群组
   */
  case class GroupKickedReceivedEvent(groupId: String, groupName: String, eventId: String) extends GroupEvent

  /**
   * 群组解散
   */
  case class GroupDestroyEvent(groupId: String, groupName: String, eventId: String) extends GroupEvent

  case class AddToGroupEvent(groupId: String, groupName: String, eventId: String) extends GroupEvent

}