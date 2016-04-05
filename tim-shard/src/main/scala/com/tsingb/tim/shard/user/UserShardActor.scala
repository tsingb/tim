package com.tsingb.tim.shard.user

import java.util.UUID
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.persistence.PersistentActor
import akka.persistence.SaveSnapshotFailure
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.SnapshotOffer
import akka.persistence.SnapshotSelectionCriteria
import akka.util.Timeout
import com.tsingb.tim.cmd._
import com.tsingb.tim.event._
import com.tsingb.tim.data._
import com.tsingb.tim.protocol._
import com.tsingb.tim.util.MessageUtil._
import akka.actor.ActorRef
import akka.actor.Terminated
import com.tsingb.tim.util.MessageUtil
import com.tsingb.tim.shard.group.GroupShardExtension
import com.tsingb.tim.shard.service.MongoService

class UserShardActor(receiveOfflineMessage: Boolean) extends PersistentActor with ActorLogging {

  import context.dispatcher
  implicit val timeout = Timeout(10 seconds)

  val actorId = self.path.name.split("/")(self.path.name.split("/").length - 1)
  val userName = actorId

  var userData = UserSnapshotData(userName)

  private var SequeuNr = 0l
  override def snapshotSequenceNr: Long = SequeuNr

  override def preStart() {
    super.preStart()
  }

  override def postStop() {
    super.postStop()
  }

  override def persistenceId = "user-" + actorId

  val userShard = UserShardExtension(context.system).shardRegion
  val groupShard = GroupShardExtension(context.system).shardRegion
  val mongoService = MongoService(context.system).region

  def receiveRecover: Receive = snapshotReceive orElse eventReceiver
  def receiveCommand: Receive = snapshotReceive orElse eventReceiver

  def snapshotReceive: Receive = {
    case SnapshotOffer(metadata, bytes: Array[Byte]) =>
      userData.fromByte(bytes)
    case SaveSnapshotSuccess(metadata) =>
      log.info("{} save Snapshot Success with[{}]", persistenceId, metadata)
    case SaveSnapshotFailure(metadata, reason) =>
      log.error(reason, "{} save Snapshot Failure with[{}]", persistenceId, metadata)
    case Terminated(ref) =>
      userData.connectionRef.ref = ActorRef.noSender
      userData.connectionRef.address = ""
      userData.isOnline = false
      saveDataSnapshot()
      log.info("[{}] out line", userName)
  }

  def eventReceiver: Receive = {
    case cmd: UserCmd =>
      dealUserCmd(sender, cmd)
    case cmd: MessageCmd =>
      dealMessageCmd(sender, cmd)
    case cmd: EventCmd =>
      dealEventCmd(sender, cmd)
    case x =>
  }

  def dealEventCmd(ref: ActorRef, cmd: EventCmd) {
    cmd match {
      case SendEventCmd(to, evt) =>
        evt match {
          case e: ContactEvent =>
            //好友事件
            dealContactEvent(ref, e)
          case e: GroupEvent =>
            //群组事件
            dealGroupEvent(ref, e)
          case e: MessageEvent =>
            //消息事件
            dealMessageEvent(ref, e)
        }
      case x =>
    }
  }

  def dealGroupEvent(ref: ActorRef, evt: GroupEvent) {
    evt match {
      case GroupInvitReceivedEvent(groupId, groupName, inviter, reason, eventId) =>
        if (userData.status == 1) {
          if (!userData.invitation) {
            userData.groups += groupId -> UserGroup(groupId, false)
            ref ! CodeResultCmd(200, "success")
          } else {
            ref ! CodeResultCmd(300, "success")
          }
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }

      case GroupInvitAccptedEvent(groupId, groupName, accepter, eventId) =>
        //只需要通知
        ref ! CodeResultCmd(200, "success")
      case GroupInvitRefusedEvent(groupId, groupName, decliner, reason, eventId) =>

      case GroupApplyReceivedEvent(groupId, groupName, applyer, reason, eventId) =>

      case GroupApplyAcceptedEvent(groupId, groupName, accepter, eventId)        =>

      case GroupApplyRefusedEvent(groupId, groupName, decliner, reason, eventId) =>

      case GroupKickedReceivedEvent(groupId, groupName, eventId) =>
        if (userData.groups.contains(groupId)) {
          userData.groups -= groupId
        }
        ref ! CodeResultCmd(200, "success")
      case GroupDestroyEvent(groupId, groupName, eventId) =>
        if (userData.groups.contains(groupId)) {
          userData.groups -= groupId
        }
        ref ! CodeResultCmd(200, "success")
    }

  }

  /**
   * 处理消息事件
   */
  def dealMessageEvent(ref: ActorRef, evt: MessageEvent) {
    //只有单聊才会产生该类事件，包括：已读回执、阅后即焚等
    evt match {
      case MessageReceivedEvent(msgId, from, eventId) =>
        userData.receiveEvents += eventId
      case MessageReadEvent(msgId, from, eventId) =>
        userData.receiveEvents += eventId
    }
  }

  /**
   * 处理好友事件
   */
  def dealContactEvent(ref: ActorRef, evt: ContactEvent) {
    evt match {
      case ContactInvitEvent(friendName, reason, eventId) =>
        //好友申请
        if (userData.contacts.contains(friendName)) {
          ref ! CodeResultCmd(200, "success")
        } else if (userData.blocks.contains(friendName)) {
          ref ! CodeResultCmd(200, "success")
        } else if (!userData.invitation) {
          //需要邀请，记录好友邀请事件
          ref ! CodeResultCmd(200, "success")
        } else {
          userData.contacts += friendName
          ref ! CodeResultCmd(200, "success")
        }
      case ContactAgreeEvent(friendName, eventId) =>
        if (!userData.contacts.contains(friendName)) {
          //好友同意邀请，记录事件
          userData.contacts += friendName
        }
        ref ! CodeResultCmd(200, "success")
      case ContactRefusedEvent(friendName, reason, eventId) =>
        //不处理，还在好友列表
        ref ! CodeResultCmd(200, "success")
      case ContactDeletedEvent(friendName, eventId) =>
        if (userData.contacts.contains(friendName)) {
          userData.contacts -= friendName
        }
    }
  }

  /**
   * 处理用户命令
   */
  def dealUserCmd(ref: ActorRef, cmd: UserCmd) {
    cmd match {
      case obj @ ConnectCmd(userName, address, device, deviceToken, clientIp) =>
        if (userData.status == 1) {
          val ref = sender
          val future = context.actorSelection(address).resolveOne()
          future onSuccess {
            case ref =>
              context.watch(ref)
              userData.connectionRef.ref = ref
              userData.isOnline = true
              userData.device = device
              userData.deviceToken = deviceToken
              log.info("[{}] in line", userName)
              saveDataSnapshot()
              sender ! CodeResultCmd(200, "success")
          }
          future onFailure {
            case x =>
              sender ! CodeResultCmd(500, "actocr-not-found")
          }
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ CreateUserCmd(userName, password, nickName, headImage) =>
        if (userData.status == 0) {
          val time = System.currentTimeMillis()
          userData.nickName = nickName
          userData.createTime = time
          userData.modifyTime = time
          userData.password = password
          userData.headImage = headImage
          userData.status = 1
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
          //将用户信息异步存储到mongo中
          mongoService ! MongoService.SaveUser(UserData("", userName, nickName, headImage, time, time, true))
        } else {
          ref ! CodeResultCmd(400, "duplicate_unique_property_exists")
        }
      case obj @ GroupShardExtension.AddUserToGroup(userName, groupId) =>
        if (userData.status == 1) {
          //被管理员添加至群组
          userData.groups += groupId -> UserGroup(groupId, false)
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ GroupShardExtension.RemoveUserFromGroup(userName, groupId) =>
        if (userData.status == 1) {
          //被管理员从群组中移除
          userData.groups -= groupId
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ DelUserCmd(userName) =>
        if (userData.status == 1) {
          val time = System.currentTimeMillis()
          userData.status = 2
          userData.modifyTime = time
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
          mongoService ! MongoService.RemoveUser(userName)
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ ResetUserPwdCmd(userName, password) =>
        if (userData.status == 1) {
          val time = System.currentTimeMillis()
          userData.password = password
          userData.modifyTime = time
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ ResetUserNicknameCmd(userName, nickName) =>
        if (userData.status == 1) {
          val time = System.currentTimeMillis()
          userData.nickName = nickName
          userData.modifyTime = time
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
          //同步mongo中信息
          mongoService ! MongoService.UpdateUserNickname(userName, nickName)
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ ResetUserHeadCmd(userName, headImage) =>
        if (userData.status == 1) {
          val time = System.currentTimeMillis()
          userData.headImage = headImage
          userData.modifyTime = time
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
          //同步mongo中信息
          mongoService ! MongoService.UpdateUserHeadImage(userName, headImage)
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ ModifyUserCmd(userName, nickName, headImage) =>
        if (userData.status == 1) {
          val time = System.currentTimeMillis()
          userData.nickName = nickName
          userData.headImage = headImage
          userData.modifyTime = time
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
          //同步mongo中信息
          mongoService ! MongoService.UpdateUser(userName, nickName, headImage)
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ BannedUserCmd(userName, ttl) =>
        if (userData.status == 1) {
          userData.banned = true
          val now = System.currentTimeMillis()
          if (ttl >= 0) {
            userData.bannedTime = ttl
            userData.startBannedTime = now
            userData.endBannedTime = now + ttl * 60 * 1000
          } else {
            userData.bannedTime = -1
          }
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ CancelBannedUserCmd(userName) =>
        if (userData.status == 1) {
          userData.banned = false
          userData.bannedTime = 0
          userData.startBannedTime = -1l
          userData.endBannedTime = -1l
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ AddUserContactCmd(userName, friendName, msg) =>
        if (userData.status == 1) {
          val ref = sender
          context.actorOf(Props {
            new Actor {
              sendEventToUser(friendName, ContactInvitEvent(userName, msg, ""))
              def receive = {
                case result @ CodeResultCmd(code, msg) if code == 404 =>
                  ref ! result
                case CodeResultCmd(code, msg) =>
                  userData.contacts += friendName
                  ref ! CodeResultCmd(200, "success")
                  saveDataSnapshot
                  mongoService ! MongoService.UserAddContact(userName, friendName)
                case x =>
              }
            }
          })

          ref ! CodeResultCmd(200, "success")

        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ DelUserContactCmd(userName, friendName) =>
        if (userData.status == 1) {
          userData.contacts -= friendName
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
          mongoService ! MongoService.UserDelContact(userName, friendName)
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ GetUserContactsCmd(userName) =>
        if (userData.status == 1) {
          //获取用户
          if (userData.contacts.size > 0) {
            context.actorOf(Props {
              new Actor {
                mongoService ! MongoService.GetUsers(userData.contacts.toSet)
                def receive = {
                  case CodeResultCmd(code, msg) =>
                    ref ! SetResultCmd(code, Set.empty[UserData])
                    context stop self
                  case SetResultCmd(code, set) if code == 200 =>
                    ref ! SetResultCmd(code, set)
                    context stop self
                  case SetResultCmd(code, set) =>
                    ref ! SetResultCmd(code, set)
                    context stop self
                }
              }
            })
          } else {
            ref ! SetResultCmd(200, Set.empty[UserData])
          }
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ AddUserBlockCmd(userName, blockName) =>
        if (userData.status == 1) {
          userData.blocks += blockName
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ DelUserBlockCmd(userName, blockName) =>
        if (userData.status == 1) {
          userData.blocks -= blockName
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ GetUserBlockCmd(userName) =>
        if (userData.status == 1) {
          //获取黑名单用户
          if (userData.blocks.size > 0) {
            context.actorOf(Props {
              new Actor {
                mongoService ! MongoService.GetUsers(userData.blocks.toSet)
                def receive = {
                  case CodeResultCmd(code, msg) =>
                    ref ! SetResultCmd(code, Set.empty[UserData])
                    context stop self
                  case SetResultCmd(code, set) if code == 200 =>
                    ref ! SetResultCmd(code, set)
                    context stop self
                  case SetResultCmd(code, set) =>
                    ref ! SetResultCmd(code, set)
                    context stop self
                }
              }
            })
          } else {
            ref ! SetResultCmd(200, Set.empty[UserData])
          }
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ GetUserStatusCmd(userName) =>
        if (userData.status == 1) {
          if (userData.isOnline) {
            ref ! DataResultCmd(200, StringData("online"))
          } else {
            ref ! DataResultCmd(200, StringData("offline"))
          }
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ GetUserCmd(userName) =>
        if (userData.status == 1) {
          val data = UserData("", userName, userData.nickName, userData.headImage, userData.createTime, userData.modifyTime, true)
          ref ! DataResultCmd(200, data)
        } else if (userData.status == 2) {
          val data = UserData("", userName, userData.nickName, userData.headImage, userData.createTime, userData.modifyTime, false)
          ref ! DataResultCmd(200, data)
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case obj @ GetUserGroupsCmd(userName) =>
        if (userData.status == 1) {
          if (userData.groups.size > 0) {
            val groupIds = scala.collection.mutable.Set.empty[String]
            userData.groups.foreach { e =>
              val (groupId, data) = e
              groupIds += groupId
            }
            //从mongodb中查询群组
            context.actorOf(Props {
              new Actor {
                mongoService ! MongoService.GetGroups(groupIds.toSet)
                def receive = {
                  case CodeResultCmd(code, msg) =>
                    ref ! SetResultCmd(code, Set.empty[GroupData])
                    context stop self
                  case SetResultCmd(code, set) if code == 200 =>
                    ref ! SetResultCmd(code, set)
                    context stop self
                  case SetResultCmd(code, set) =>
                    ref ! SetResultCmd(code, set)
                    context stop self
                }
              }
            })
          } else {
            ref ! SetResultCmd(200, Set.empty[GroupData])
          }

        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
    }
  }

  /**
   * 处理消息命令
   */
  def dealMessageCmd(ref: ActorRef, cmd: MessageCmd) {
    cmd match {
      case SendMessageCmd(to: String, msg: Message) =>
        val ref = sender
        if (userData.status == 1) {
          context.actorOf(Props {
            new Actor {
              var needSave = false
              msg match {
                case m: UserMessage =>
                  userShard ! ForwardMessageCmd(msg.to, msg)
                  needSave = true
                case m: GroupMessage =>
                  groupShard ! ForwardMessageCmd(msg.to, msg)
                  needSave = false
              }
              def receive = {
                case CodeResultCmd(code, message) if code == 200 =>
                  ref ! CodeResultCmd(200, "success")
                  if (needSave) {

                  }
                case CodeResultCmd(code, message) if code == 404 =>
                  //如果已用户不存在，从好友列表及黑名单中删除用户
                  if (userData.blocks.contains(msg.to)) {
                    userData.blocks -= msg.to
                  }

                  if (userData.contacts.contains(msg.to)) {
                    userData.contacts -= msg.to
                    mongoService ! MongoService.UserDelContact(userName, msg.to)
                  }
                  ref ! CodeResultCmd(code, message)
                case CodeResultCmd(code, message) =>
                  ref ! CodeResultCmd(code, message)
                case x =>
              }
            }
          })
        } else {
          sender ! CodeResultCmd(404, "service_resource_not_found")
        }
      case ForwardMessageCmd(to: String, msg: Message) =>
        val ref = sender
        if (userData.status == 1) {
          msg match {
            case m: UserMessage =>
              if (userData.blocks.contains(m.from)) {
                sender ! CodeResultCmd(300, "errro")
              } else {
                ref ! CodeResultCmd(200, "success")
                //推送消息
                pushUserMessage(m)
              }
            case m: GroupMessage =>
              if (userData.groups.contains(m.to)) {
                if (userData.groups(m.to).isBlock) {
                  //已屏蔽群组消息
                  ref ! CodeResultCmd(200, "success")
                } else {
                  ref ! CodeResultCmd(200, "success")
                  //推送消息
                  pushGroupMessage(m)
                }
              } else {
                //不在群组中
                ref ! CodeResultCmd(404, "service_resource_not_found")
              }

          }

        } else {
          sender ! CodeResultCmd(404, "service_resource_not_found")
        }
      case MessageReceivedCmd(to, msgId) =>
        if (userData.pushMessages.contains(msgId)) {
          val smsg = userData.pushMessages(msgId)
          smsg.modal match {
            case modal @ Message.NoAckMsg(ok) =>
              //普通消息
              userData.pushMessages -= msgId
            case modal @ Message.AckAfterReceivedMsg(ok) =>
              //已收回执消息
              userData.pushMessages -= msgId
              sendEventToUser(smsg.to, MessageReceivedEvent(smsg.msgId, smsg.from, ""))
            case modal @ Message.AckAfterReadMsg(ok) =>
              //已读回馈消息
              userData.waitDelMessages += msgId -> smsg
              userData.pushMessages -= msgId
              sendEventToUser(smsg.to, MessageReceivedEvent(smsg.msgId, smsg.from, ""))
            case modal @ Message.BurnAfterReadMsg(ok) =>
              //阅后即焚消息
              userData.waitDelMessages += msgId -> smsg
              userData.pushMessages -= msgId
              sendEventToUser(smsg.to, MessageReceivedEvent(smsg.msgId, smsg.from, ""))
          }
        }
        ref ! CodeResultCmd(200, "success")
      case MessageReadCmd(to, msgId) =>
        if (userData.waitDelMessages.contains(msgId)) {
          val smsg = userData.waitDelMessages(msgId)
          smsg.modal match {
            case modal @ Message.AckAfterReadMsg(ok) =>
              //已读回馈消息
              userData.waitDelMessages -= msgId
              sendEventToUser(smsg.to, MessageReadEvent(smsg.msgId, smsg.from, ""))
            case modal @ Message.BurnAfterReadMsg(ok) =>
              //阅后即焚消息
              userData.waitDelMessages -= msgId
              sendEventToUser(smsg.to, MessageReadEvent(smsg.msgId, smsg.from, ""))
            case x =>
              //其他消息直接删除
              userData.waitDelMessages -= msgId
          }
        }
        ref ! CodeResultCmd(200, "success")
    }
  }

  /**
   * 发送事件，不需要关心返回结果
   */
  def sendEventToUser(to: String, evt: Event) {
    context.actorOf(Props {
      new Actor {
        userShard ! SendEventCmd(to, evt)
        def receive = {
          case x =>
        }
      }
    })
  }

  /**
   * 推送用户消息
   */
  def pushUserMessage(msg: UserMessage) {
    if (userData.isOnline) {
      userData.connectionRef.ref ! PushMessage(msg)
      //推送消息
      userData.pushMessages += msg.msgId -> SnapshotMessage(msg.msgId, msg.from, msg.to, msg.modal, System.currentTimeMillis())
    } else if (receiveOfflineMessage) {
      userData.receiveMessages += msg.msgId -> SnapshotMessage(msg.msgId, msg.from, msg.to, msg.modal, System.currentTimeMillis())
    } else {

    }
  }

  /**
   * 推送群组消息
   */
  def pushGroupMessage(msg: GroupMessage) {
    var isPush = true
    if (userData.groups.contains(msg.to)) {
      isPush = !userData.groups(msg.to).isBlock
    }
    if (isPush) {
      if (userData.isOnline) {
        userData.connectionRef.ref ! PushMessage(msg)
        userData.pushGroupMessages += msg.msgId -> msg.to
        //推送消息
      } else if (receiveOfflineMessage) {
        userData.receiveGroupMessages += msg.msgId -> msg.to
      } else {
        //不接收离线消息，不需要处理
      }
    } else {
      //已设置屏蔽该群组消息
    }

  }

  def pushEvent() {

  }

  //记录消息快照
  def saveDataSnapshot() {
    val start = System.currentTimeMillis()
    deleteSnapshots(SnapshotSelectionCriteria.Latest)
    val end = System.currentTimeMillis()
    log.info("{} snapshot time {} ms", persistenceId, (end - start))
  }

}