package com.tsingb.tim.http

import akka.actor.ActorLogging
import com.tsingb.tim.http.router.UserRouter
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.actor.Actor
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.Props
import com.tsingb.tim.http.router.GroupRouter
import akka.http.scaladsl.server.Route
import com.tsingb.tim.http.router.HttpRouter

object HttpServer extends App {
  val config = ConfigFactory.load("application.conf")
  val serverUri = config.getString("akka.http.uri")
  val serverPort = config.getInt("akka.http.port")
  val system = ActorSystem("tim-http", config)
  system.actorOf(Props(classOf[HttpService], serverUri, serverPort))
}

class HttpService(serverUri: String, serverPort: Int) extends Actor with ActorLogging {
  def actorRefFactory = context
  import context.dispatcher
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()

  override def preStart = {
    super.preStart()
    val bindingFuture = Http().bindAndHandle(routes, serverUri, serverPort)

  }

  def receive = {
    case x =>
  }

  val routes: Route = (new HttpRouter(context)).route

}

