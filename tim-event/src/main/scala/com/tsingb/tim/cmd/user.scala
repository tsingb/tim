package com.tsingb.tim.cmd

/**
 *
 */
case class ConnectCmd(userName: String, address: String, device: String, deviceToken: String, clientIp: String) extends UserCmd

/**
 * 创建用户
 */
case class CreateUserCmd(userName: String, password: String, nickName: String, headImage: String) extends UserCmd

/**
 * 删除用户
 */
case class DelUserCmd(userName: String) extends UserCmd
/**
 * 重置密码
 */
case class ResetUserPwdCmd(userName: String, password: String) extends UserCmd

/**
 * 修改用户昵称
 */
case class ResetUserNicknameCmd(userName: String, nickName: String) extends UserCmd

/**
 *
 */
case class ResetUserHeadCmd(userName: String, headImage: String) extends UserCmd

/**
 * 
 */
case class ModifyUserCmd(userName: String, nickName: String, headImage: String) extends UserCmd

/**
 * 用户禁言
 */
case class BannedUserCmd(userName: String, ttl: Long) extends UserCmd

case class CancelBannedUserCmd(userName: String) extends UserCmd

case class AddUserContactCmd(userName: String, friendName: String, msg: String) extends UserCmd

case class DelUserContactCmd(userName: String, friendName: String) extends UserCmd

case class GetUserContactsCmd(userName: String) extends UserCmd

case class AddUserBlockCmd(userName: String, blockName: String) extends UserCmd

case class DelUserBlockCmd(userName: String, blockName: String) extends UserCmd

case class GetUserBlockCmd(userName: String) extends UserCmd

case class GetUserStatusCmd(userName: String) extends UserCmd

case class GetUserCmd(userName: String) extends UserCmd

case class GetUserGroupsCmd(userName: String) extends UserCmd



