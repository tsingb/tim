package com.tsingb.tim

import com.tsingb.tim.data._
import com.tsingb.tim.event._

package object protocol {

  trait Protocol extends Serializable

  case class PushMessage(msg: Message) extends Protocol

  case class PushEvent(evt: Event) extends Protocol

  case class PushMessages(msgs: Set[Message]) extends Protocol

  case class PushEvents(evts: Set[Event]) extends Protocol

}