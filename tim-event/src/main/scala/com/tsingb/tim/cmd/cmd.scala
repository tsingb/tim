package com.tsingb.tim

package object cmd {

  trait Command extends Serializable

  /**
   * 用户命令
   */
  trait UserCmd extends Command {
    def userName: String
  }

  trait GroupCmd extends Command {
    def groupId: String
  }

  trait MessageCmd extends Command {
    def to: String
  }

  trait ResultCmd extends Command {
    def code: Int
  }

  trait EventCmd extends Command {
    def to: String
  }

  trait MessageAckCmd extends Command {
    def msgId: String
  }

}