package com.tsingb.tim.cmd

import com.tsingb.tim.event._

case class SendEventCmd(to: String, evt: Event) extends EventCmd