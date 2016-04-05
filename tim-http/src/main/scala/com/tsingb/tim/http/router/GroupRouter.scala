package com.tsingb.tim.http.router

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.stream.ActorMaterializer
import scala.concurrent.ExecutionContext
import akka.pattern.ask
import akka.actor.ActorContext
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.RequestContext
import com.google.gson.Gson
import akka.http.scaladsl.server.Route
import akka.actor.Props
import com.tsingb.tim.cluster.ShardClusterClient
import com.tsingb.tim.cmd._
import com.tsingb.tim.data._
import com.tsingb.tim.event._
import com.tsingb.tim.protocol._
import com.tsingb.tim.util._
import com.tsingb.tim.http.protocol._
import com.tsingb.tim.http.protocol.JsonProtocol._
import akka.util.Timeout
import akka.actor.Actor
import scala.concurrent.Future
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.model.HttpResponse
import com.tsingb.tim.http.util.HttpResponseUtil
import akka.actor.ActorRef
import java.util.UUID

/**
 *
 */
class GroupRouter(val context: ActorContext)(implicit executionContext: ExecutionContext, materializer: ActorMaterializer) extends Directives with BaseRouter {
  val shardingClient = ShardClusterClient(context.system)
  val shardMediator = shardingClient.shardMediator

  val groups = path("groups")
  val group_info = path("groups" / Segment)
  val group_members = path("groups" / Segment / "members")
  val group_member = path("groups" / Segment / "members" / Segment)
  val group_blocks = path("groups" / Segment / "blocks")
  val group_block = path("groups" / Segment / "blocks" / Segment)

  val route: Route = {
    groups {
      auth("") {
        post {
          entity(as[String]) { param =>
            ctx =>
              doCreateGroup(ctx, param)
          }
        }
      }
    } ~
      group_info { groupId =>
        auth("") {
          post {
            entity(as[String]) { param =>
              ctx =>
                doCreateGroup(ctx, param)
            }
          }
        }
      } ~
      group_member { (groupId, usernames) =>
        auth("") {
          post {
            ctx =>
              doAddGroupMember(ctx, groupId, usernames)
          } ~
            delete {
              ctx =>
                doRemoveGroupMember(ctx, groupId, usernames)
            }
        }
      } ~
      group_members { groupId =>
        auth("") {
          get {
            ctx =>
              doGetGroupMembers(ctx, groupId)
          } ~
            post {
              entity(as[String]) { param =>
                ctx =>
                  doAddGroupMembers(ctx, groupId, param)
              }
            }
        }
      } ~
      group_block { (groupId, usernames) =>
        auth("") {
          post {
            ctx =>
              doAddGroupBlock(ctx, groupId, usernames)
          } ~
            delete {
              ctx =>
                doRemoveGroupBlock(ctx, groupId, usernames)
            }
        }
      }
    group_blocks { groupId =>
      auth("") {
        get {
          ctx =>
            doGetGroupBlocks(ctx, groupId)
        } ~
          post {
            entity(as[String]) { param =>
              ctx =>
                doAddGroupBlocks(ctx, groupId, param)
            }
          }
      }
    }
    pathPrefix("groups") {
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

  def doRemoveGroupMember(ctx: RequestContext, groupId: String, usernames: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    implicit val timeout = Timeout(5.seconds)
    val ref = context.actorOf {
      Props {
        new Actor {
          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val groupMember = RemoveMembersCmd(groupId: String, usernames.split(",").toSet)
              shardMediator ! shardingClient.serviceSend(groupMember)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self

          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doRemoveGroupBlock(ctx: RequestContext, groupId: String, usernames: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    implicit val timeout = Timeout(5.seconds)
    val ref = context.actorOf {
      Props {
        new Actor {
          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val groupMember = RemoveGroupBlocksCmd(groupId: String, usernames.split(",").toSet)
              shardMediator ! shardingClient.serviceSend(groupMember)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self

          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doAddGroupMember(ctx: RequestContext, groupId: String, username: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)

    implicit val timeout = Timeout(5.seconds)
    val ref = context.actorOf {
      Props {
        new Actor {
          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val groupMember = AddMembersCmd(groupId: String, Set(username))
              shardMediator ! shardingClient.serviceSend(groupMember)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self

          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doGetGroupMembers(ctx: RequestContext, groupId: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)

    implicit val timeout = Timeout(5.seconds)
    val ref = context.actorOf {
      Props {
        new Actor {
          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val groupMember = GetGroupMembersCmd(groupId: String)
              shardMediator ! shardingClient.serviceSend(groupMember)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self

          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doGetGroupBlocks(ctx: RequestContext, groupId: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)

    implicit val timeout = Timeout(5.seconds)
    val ref = context.actorOf {
      Props {
        new Actor {
          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val groupMember = GetGroupBlocksCmd(groupId: String)
              shardMediator ! shardingClient.serviceSend(groupMember)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self

          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doAddGroupBlock(ctx: RequestContext, groupId: String, username: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)

    implicit val timeout = Timeout(5.seconds)
    val ref = context.actorOf {
      Props {
        new Actor {
          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val groupMember = AddGroupBlocksCmd(groupId: String, Set(username))
              shardMediator ! shardingClient.serviceSend(groupMember)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self

          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doAddGroupMembers(ctx: RequestContext, groupId: String, param: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    val json = toJson(param)

    if (json.has("usernames")) {
      val usernames = json.get("usernames").getAsJsonArray
      val it = usernames.iterator()
      val set = scala.collection.mutable.Set.empty[String]
      while (it.hasNext()) {
        val username = it.next().getAsString
        set += username
      }
      implicit val timeout = Timeout(5.seconds)
      val ref = context.actorOf {
        Props {
          new Actor {
            var ackRef = ActorRef.noSender
            def receive = {
              case "req" =>
                ackRef = sender
                val groupMember = AddMembersCmd(groupId: String, set.toSet)
                shardMediator ! shardingClient.serviceSend(groupMember)
              case result @ CodeResultCmd(code: Int, msg: String) =>
                ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
                context stop self

            }
          }
        }
      }
      ask(ref, "req").mapTo[RouteResult]
    } else {
      val f: Future[RouteResult] = Future {
        RouteResult.Complete(HttpResponseUtil.entityResponse(500, toJson(CodeResult(500, "param-error"))))
      }
      f
    }

  }

  def doAddGroupBlocks(ctx: RequestContext, groupId: String, param: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    val json = toJson(param)

    if (json.has("usernames")) {
      val usernames = json.get("usernames").getAsJsonArray
      val it = usernames.iterator()
      val set = scala.collection.mutable.Set.empty[String]
      while (it.hasNext()) {
        val username = it.next().getAsString
        set += username
      }
      implicit val timeout = Timeout(5.seconds)
      val ref = context.actorOf {
        Props {
          new Actor {
            var ackRef = ActorRef.noSender
            def receive = {
              case "req" =>
                ackRef = sender
                val groupMember = AddGroupBlocksCmd(groupId: String, set.toSet)
                shardMediator ! shardingClient.serviceSend(groupMember)
              case result @ CodeResultCmd(code: Int, msg: String) =>
                ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
                context stop self

            }
          }
        }
      }
      ask(ref, "req").mapTo[RouteResult]
    } else {
      val f: Future[RouteResult] = Future {
        RouteResult.Complete(HttpResponseUtil.entityResponse(500, toJson(CodeResult(500, "param-error"))))
      }
      f
    }

  }

  def doCreateGroup(ctx: RequestContext, param: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    val group = toGroup(param)
    implicit val timeout = Timeout(5.seconds)
    val ref = context.actorOf {
      Props {
        new Actor {
          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val groupId = UUID.randomUUID().toString()
              val createGroup = CreateGroupCmd(groupId, group.groupName, group.owner, group.desc, group.public, group.maxUsers)
              shardMediator ! shardingClient.serviceSend(createGroup)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self

          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }
}