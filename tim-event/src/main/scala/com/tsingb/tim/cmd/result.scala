package com.tsingb.tim.cmd

import com.tsingb.tim.data._

case class CodeResultCmd(code: Int, msg: String) extends ResultCmd

case class DataResultCmd(code: Int, value: Data) extends ResultCmd

case class SetResultCmd(code: Int, value: Set[_ <: Data]) extends ResultCmd