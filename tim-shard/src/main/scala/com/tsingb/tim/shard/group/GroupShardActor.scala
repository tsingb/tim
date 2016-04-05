package com.tsingb.tim.shard.group

import java.util.UUID
import scala.concurrent.duration.DurationInt
import akka.pattern.ask
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
import com.tsingb.tim.util.MessageUtil._
import akka.actor.ActorRef
import akka.actor.Terminated
import com.tsingb.tim.util.MessageUtil
import com.tsingb.tim.shard.user.UserShardExtension
import com.tsingb.tim.shard.user.UserSnapshotData
import scala.concurrent.Await
import com.tsingb.tim.shard.service.MongoService

class GroupShardActor extends PersistentActor with ActorLogging {
  import context.dispatcher
  implicit val timeout = Timeout(10 seconds)

  val actorId = self.path.name.split("/")(self.path.name.split("/").length - 1)
  val groupId = actorId

  var groupData = GroupSnapshotData(groupId)

  private var SequeuNr = 0l
  override def snapshotSequenceNr: Long = SequeuNr

  override def preStart() {
    super.preStart()
  }

  override def postStop() {
    super.postStop()
  }

  override def persistenceId = "group-" + actorId

  val userShard = UserShardExtension(context.system).shardRegion
  val mongoService = MongoService(context.system).region

  def receiveRecover: Receive = snapshotReceive orElse eventReceiver
  def receiveCommand: Receive = snapshotReceive orElse eventReceiver

  def snapshotReceive: Receive = {
    case SnapshotOffer(metadata, s: Array[Byte]) =>
    case SaveSnapshotSuccess(metadata) =>
      log.info("{} save Snapshot Success with[{}]", persistenceId, metadata)
    case SaveSnapshotFailure(metadata, reason) =>
      log.error(reason, "{} save Snapshot Failure with[{}]", persistenceId, metadata)
  }

  def eventReceiver: Receive = {
    case cmd: GroupCmd =>
      dealGroupCmd(sender, cmd)
    case cmd: MessageCmd =>
      dealMessageCmd(sender, cmd)
    case cmd: EventCmd =>

    case x             =>
  }

  /**
   * 处理群组命令
   */
  def dealGroupCmd(ref: ActorRef, cmd: GroupCmd) {
    cmd match {
      case CreateGroupCmd(groupId, groupName, owner, desc, public, maxUsers) =>
        if (groupData.status == 0) {
          val now = System.currentTimeMillis()
          groupData.groupName = groupName
          groupData.owner = owner
          groupData.desc = desc
          groupData.public = public
          groupData.maxUsers = maxUsers
          groupData.createTime = now
          groupData.modifyTime = now
          groupData.status = 1
          val member = GroupMember(owner, "manager", false, 0, 0, 0, now)
          groupData.members += (owner -> member)
          ref ! CodeResultCmd(200, "success")
          saveDataSnapshot
        } else {
          ref ! CodeResultCmd(400, "duplicate_unique_property_exists")
        }
      case GetGroupCmd(groupId) =>
        if (groupData.status == 1) {
          val data = GroupData(groupId, groupData.groupName, groupData.owner, groupData.desc, groupData.public, groupData.maxUsers, groupData.createTime, groupData.modifyTime, true)
          ref ! DataResultCmd(200, data)
        } else if (groupData.status == 2) {
          val data = GroupData(groupId, groupData.groupName, groupData.owner, groupData.desc, groupData.public, groupData.maxUsers, groupData.createTime, groupData.modifyTime, false)
          ref ! DataResultCmd(200, data)
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case JoinGroupCmd(groupId, applyer) =>

      case AddMembersCmd(groupId: String, userNames) =>
        if (groupData.status == 1) {
          context.actorOf(Props {
            new Actor {
              def receive = {
                case x =>
                  val now = System.currentTimeMillis()
                  val resultSet = scala.collection.mutable.Set.empty[MemberResultData]
                  userNames.foreach { userName =>
                    val result = ask(userShard, GroupShardExtension.AddUserToGroup(groupId, userName)).asInstanceOf[CodeResultCmd]
                    if (result.code == 200) {
                      val member = GroupMember(userName, "member", false, 0, 0, 0, now)
                      groupData.members += (userName -> member)
                      resultSet += MemberResultData(userName, true, "")
                    } else if (result.code == 404) {
                      resultSet += MemberResultData(userName, false, "user not exist")
                    }

                  }
                  ref ! SetResultCmd(200, resultSet.toSet)
              }
            }
          })
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case RemoveMembersCmd(groupId: String, userNames)   =>

      case AddUserToGroupCmd(groupId, userName, operator) =>

      case ApplyJoinToGroupCmd(groupId, applyer, reason) =>
        if (groupData.status == 1) {
          if (groupData.blocks.contains(applyer)) {
            //在群组黑名单中，不处理
            ref ! CodeResultCmd(200, "success")
          } else {
            if (groupData.public) {
              val now = System.currentTimeMillis()
              val member = GroupMember(applyer, "member", false, 0, 0, 0, now)
              groupData.members += (applyer -> member)
              val evt = GroupApplyAcceptedEvent(groupId, groupData.groupName, groupData.owner, UUID.randomUUID().toString())
              userShard ! SendEventCmd(applyer, evt)
            } else if (groupData.members.contains(applyer)) {
              //已在群组中，不需要处理
            } else {
              //发送申请事件至管理员
              val evt = GroupApplyReceivedEvent(groupId, groupData.groupName, applyer, reason, UUID.randomUUID().toString())
              userShard ! SendEventCmd(groupData.owner, evt)
            }
            ref ! CodeResultCmd(200, "success")
          }

        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case AcceptGroupApplyCmd(groupId, applyer, operator) =>
        if (groupData.status == 1) {
          val now = System.currentTimeMillis()
          if (groupData.members.contains(applyer)) {
            //已在成员列表中，不需要处理
          } else {
            val member = GroupMember(applyer, "member", false, 0, 0, 0, now)
            groupData.members += (applyer -> member)
            val evt = GroupApplyAcceptedEvent(groupId, groupData.groupName, operator, UUID.randomUUID().toString())
            userShard ! SendEventCmd(applyer, evt)
          }
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case DeclineGroupApplyCmd(groupId, applyer, operator, reason) =>
        if (groupData.status == 1) {
          val evt = GroupApplyRefusedEvent(groupId, groupData.groupName, operator, reason, UUID.randomUUID().toString())
          userShard ! SendEventCmd(applyer, evt)
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case InviteUserJoinToGroupCmd(groupId, userName, inviter, reason) =>
        if (groupData.status == 1) {
          if (groupData.members.contains(userName)) {
            //已在成员列表中，不需要处理
            ref ! CodeResultCmd(200, "success")
          } else {
            if (inviter != groupData.owner) {
              //非管理员，没有权限邀请
              ref ! CodeResultCmd(403, "success")
            } else {
              val now = System.currentTimeMillis()
              context.actorOf(Props {
                new Actor {
                  val evt = GroupInvitReceivedEvent(groupId, groupData.groupName, inviter, reason, UUID.randomUUID().toString())
                  userShard ! SendEventCmd(userName, evt)
                  def receive = {
                    case CodeResultCmd(code, msg) if code == 200 =>
                      //成员直接同意，不需要申请
                      val member = GroupMember(userName, "member", false, 0, 0, 0, now)
                      groupData.members += (userName -> member)
                    case x =>
                  }
                }
              })
            }
            ref ! CodeResultCmd(200, "success")
          }
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case AcceptGroupInvitationCmd(groupId, userName, inviter) =>
        if (groupData.status == 1) {
          val now = System.currentTimeMillis()
          val member = GroupMember(userName, "member", false, 0, 0, 0, now)
          groupData.members += (userName -> member)
          val evt = GroupInvitAccptedEvent(groupId, groupData.groupName, userName, UUID.randomUUID().toString())
          userShard ! SendEventCmd(inviter, evt)
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case DeclineGroupInvitationCmd(groupId, userName, inviter, reason) =>
        if (groupData.status == 1) {
          val evt = GroupInvitRefusedEvent(groupId, groupData.groupName, userName, reason, UUID.randomUUID().toString())
          userShard ! SendEventCmd(inviter, evt)
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case RemoveMemberFromGroupCmd(groupId, userName, operator) =>

      case LeaveGroupCmd(groupId, userName) =>
        if (groupData.status == 1) {
          if (groupData.members.contains(userName)) {
            groupData.members -= userName
            userShard ! GroupShardExtension.LeaveFromGrouCmd(userName, groupId)
            //发送消息
          }
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case DestroyGroupCmd(groupId, userName) =>

      case ModifyGroupNameCmd(groupId, groupName) =>
        if (groupData.status == 1) {
          groupData.groupName = groupName
          saveDataSnapshot
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case ModifyGroupDescCmd(groupId, desc) =>
        if (groupData.status == 1) {
          groupData.desc = desc
          saveDataSnapshot
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case ModifyGroupMaxuserCmd(groupId, maxusers) =>
        if (groupData.status == 1) {
          if (groupData.members.size > maxusers) {
            //当前成员总数大于最大用户数
          } else {
            groupData.maxUsers = maxusers
            saveDataSnapshot
          }
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case ModifyGroupOwnerCmd(groupId, owner) =>
        if (groupData.status == 1) {
          if (groupData.members.contains(owner)) {
            val member = groupData.members(owner)
            member.role = "manager"
          } else {
            val now = System.currentTimeMillis()
            val result = ask(userShard, GroupShardExtension.AddUserToGroup(groupId, owner)).asInstanceOf[CodeResultCmd]
            if (result.code == 200) {
              val member = GroupMember(owner, "manager", false, 0, 0, 0, now)
              groupData.members += (owner -> member)
            }
          }
          groupData.owner = owner
          saveDataSnapshot
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case ModifyGroupCmd(groupId, groupName, desc, maxUsers) =>
        if (groupData.status == 1) {
          if (groupData.members.size > maxUsers) {
            //当前成员总数大于最大用户数
          } else {
            groupData.maxUsers = maxUsers
          }
          groupData.desc = desc
          groupData.groupName = groupName
          saveDataSnapshot
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case GetGroupMembersCmd(groupId) =>
        if (groupData.status == 1) {

          context.actorOf(Props {
            new Actor {
              val set = scala.collection.mutable.Set.empty[String]
              groupData.members.foreach { e =>
                val (userName, member) = e
                set += userName
              }
              mongoService ! MongoService.GetUsers(set.toSet)
              def receive = {
                case CodeResultCmd(code, msg) =>
                  ref ! SetResultCmd(code, Set.empty[GroupMemberData])
                  context stop self
                case SetResultCmd(code, set) if code == 200 =>
                  val resultSet = scala.collection.mutable.Set.empty[GroupMemberData]

                  ref ! SetResultCmd(code, set)
                  context stop self
                case SetResultCmd(code, set) =>
                  ref ! SetResultCmd(code, set)
                  context stop self
              }
            }
          })

        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case AddGroupBlockCmd(groupId, block) =>
        if (groupData.status == 1) {
          if (!groupData.blocks.contains(block)) {
            groupData.blocks += block
            saveDataSnapshot
          }
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case RemoveGroupBlockCmd(groupId, block) =>
        if (groupData.status == 1) {
          if (groupData.blocks.contains(block)) {
            groupData.blocks -= block
            saveDataSnapshot
          }
          ref ! CodeResultCmd(200, "success")
        } else {
          ref ! CodeResultCmd(404, "service_resource_not_found")
        }
      case GetGroupBlocksCmd(groupId)                    =>
        
      case BannedGroupMemberCmd(groupId, userName, time) =>
      case CancelBannedGroupMemberCmd(groupId, userName) =>
      case BannedGroupCmd(groupId, time)                 =>
      case CancelBannedGroupCmd(groupId)                 =>
    }
  }

  /**
   * 转发消息至群组成员
   */
  def forwardMessageToMember(msg: GroupMessage) {
    context.actorOf(Props {
      new Actor {
        var sendCount = 0
        var resultCount = 0
        groupData.members.foreach { e =>
          val (userName, member) = e
          if (userName != msg.from) {
            userShard ! ForwardMessageCmd(userName, msg)
            sendCount += 1
          }
        }

        def receive = {
          case CodeResultCmd(code, msg) =>
            resultCount += 1
            if (resultCount >= sendCount) {
              context stop self
            }
        }
      }
    })
  }

  /**
   * 处理消息命令
   */
  def dealMessageCmd(ref: ActorRef, cmd: MessageCmd) {
    if (groupData.status == 1) {
      cmd match {
        case ForwardMessageCmd(to: String, msg: Message) =>
          msg match {
            case m: GroupMessage =>
              if (groupData.members.contains((m.from))) {
                val member = groupData.members(m.from)
                if (!member.banned) {
                  forwardMessageToMember(m)
                }
              }
          }
      }
    } else {
      ref ! CodeResultCmd(404, "service_resource_not_found")
    }

  }

  //记录消息快照
  def saveDataSnapshot() {
    val start = System.currentTimeMillis()
    deleteSnapshots(SnapshotSelectionCriteria.Latest)
    val end = System.currentTimeMillis()
    log.info("{} snapshot time {} ms", persistenceId, (end - start))
  }

}