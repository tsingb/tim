package com.tsingb.tim.http.router

import akka.event.LogSource
import akka.actor.ActorContext
import akka.event.Logging
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.AuthenticationFailedRejection
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected

trait BaseRouter { _: Directives =>
  def context: ActorContext

  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

  val httpChallenge = akka.http.scaladsl.model.headers.HttpChallenge("", "")

  val log = Logging(context.system, this)

  def respondWithToken(token: String) =
    respondWithHeader(RawHeader("token", token))

  def respondWithAjax() =
    respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*"))

  def auth(username: String): Directive0 = headerValueByName("token").flatMap {
    case token: String =>
      pass
    case _ =>
      reject(AuthenticationFailedRejection(CredentialsRejected, httpChallenge))
  }
}