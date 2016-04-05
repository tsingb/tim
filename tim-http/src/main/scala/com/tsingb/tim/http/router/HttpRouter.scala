package com.tsingb.tim.http.router

import akka.stream.ActorMaterializer
import scala.concurrent.ExecutionContext
import akka.actor.ActorContext
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route
import com.tsingb.tim.http.util.HttpResponseUtil
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.server.RequestContext
import scala.concurrent.Future
import com.tsingb.tim.cmd._
import com.tsingb.tim.data._
import com.tsingb.tim.event._
import com.tsingb.tim.protocol._
import com.tsingb.tim.util._
import com.tsingb.tim.http.protocol._
import com.tsingb.tim.http.protocol.JsonProtocol._

class HttpRouter(val context: ActorContext)(implicit executionContext: ExecutionContext, materializer: ActorMaterializer) extends Directives with BaseRouter {

  val userRoute = new UserRouter(context).route
  val groupRoute = new GroupRouter(context).route

  val route: Route = {
    userRoute ~
      groupRoute ~
      extractUnmatchedPath { url =>
        ctx =>
          unhandleRequest(ctx)
      }
  }

  def unhandleRequest(ctx: RequestContext): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    val f: Future[RouteResult] = Future {
      RouteResult.Complete(HttpResponseUtil.entityResponse(404, toJson(CodeResult(404, "service_resource_not_found"))))
    }
    f
  }
}