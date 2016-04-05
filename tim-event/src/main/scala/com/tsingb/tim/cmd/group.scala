package com.tsingb.tim.cmd

/**
 * 创建群组
 */
case class CreateGroupCmd(groupId: String, groupName: String, owner: String, desc: String, public: Boolean, maxUsers: Int) extends GroupCmd

/**
 * 获取群组详情
 */
case class GetGroupCmd(groupId: String) extends GroupCmd

/**
 *
 */
case class AddMembersCmd(groupId: String, userName: Set[String]) extends GroupCmd

/**
 *
 */
case class RemoveMembersCmd(groupId: String, userName: Set[String]) extends GroupCmd

/**
 * 加入群组
 */
case class JoinGroupCmd(groupId: String, applyer: String) extends GroupCmd

/**
 * 将人加入群组
 */
case class AddUserToGroupCmd(groupId: String, userName: String, operator: String) extends GroupCmd

/**
 * 申请加群
 */
case class ApplyJoinToGroupCmd(groupId: String, applyer: String, reason: String) extends GroupCmd

/**
 * 同意入群申请
 */
case class AcceptGroupApplyCmd(groupId: String, applyer: String, operator: String) extends GroupCmd

/**
 * 拒绝入群申请
 */
case class DeclineGroupApplyCmd(groupId: String, applyer: String, operator: String, reason: String) extends GroupCmd

/**
 * 邀请人加入群
 */
case class InviteUserJoinToGroupCmd(groupId: String, userName: String, inviter: String, reason: String) extends GroupCmd

/**
 * 同意入群邀请
 */
case class AcceptGroupInvitationCmd(groupId: String, userName: String, inviter: String) extends GroupCmd

/**
 * 拒绝入群申请
 */
case class DeclineGroupInvitationCmd(groupId: String, userName: String, inviter: String, reason: String) extends GroupCmd

/**
 * 踢出群组
 */
case class RemoveMemberFromGroupCmd(groupId: String, userName: String, operator: String) extends GroupCmd

/**
 * 离开群组
 */
case class LeaveGroupCmd(groupId: String, userName: String) extends GroupCmd

/**
 * 解散群组
 */
case class DestroyGroupCmd(groupId: String, userName: String) extends GroupCmd

/**
 * 修改群组名词
 */
case class ModifyGroupNameCmd(groupId: String, groupName: String) extends GroupCmd

/**
 * 修改群组描述
 */
case class ModifyGroupDescCmd(groupId: String, desc: String) extends GroupCmd

/**
 * 修改群组最大用户数
 */
case class ModifyGroupMaxuserCmd(groupId: String, maxusers: Int) extends GroupCmd

/**
 * 转让群组
 */
case class ModifyGroupOwnerCmd(groupId: String, owner: String) extends GroupCmd

/**
 * 修改群组
 */
case class ModifyGroupCmd(groupId: String, groupName: String, desc: String, maxUsers: Int) extends GroupCmd

/**
 * 获取群组成员
 */
case class GetGroupMembersCmd(groupId: String) extends GroupCmd

/**
 * 增加群组黑名单
 */
case class AddGroupBlockCmd(groupId: String, block: String) extends GroupCmd

case class AddGroupBlocksCmd(groupId: String, blocks: Set[String]) extends GroupCmd

/**
 * 移除群组黑名单
 */
case class RemoveGroupBlockCmd(groupId: String, block: String) extends GroupCmd

case class RemoveGroupBlocksCmd(groupId: String, blocks: Set[String]) extends GroupCmd

/**
 * 获取群组黑名单
 */
case class GetGroupBlocksCmd(groupId: String) extends GroupCmd

/**
 * 群组用户禁言
 */
case class BannedGroupMemberCmd(groupId: String, userName: String, time: Long) extends GroupCmd

/**
 * 取消群组用户禁言
 */
case class CancelBannedGroupMemberCmd(groupId: String, userName: String) extends GroupCmd

/**
 * 群组禁言
 */
case class BannedGroupCmd(groupId: String, time: Long) extends GroupCmd

/**
 * 取消群组禁言
 */
case class CancelBannedGroupCmd(groupId: String) extends GroupCmd


