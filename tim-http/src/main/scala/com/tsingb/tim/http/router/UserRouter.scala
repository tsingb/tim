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

/**
 * 处理用户相关请求
 *
 * /users
 * 创建用户:	post -> [{"username":"homer010","password":"123456", "nickname":"小李子","headimage":"http://"}]		
 * 
 * /users/${username}
 * 用户详情：get -> {}
 * 修改用户：put -> {"nickname":"小李子","headimage":"http://"}
 * 删除用户：delete -> {}
 * 
 * /users/${username}/pwd
 * 修改密码：put -> {"newpassword":"12345678"}
 *
 * /users/${username}/contacts/${friendname}
 * 添加好友：post	->  {"friendname":"homer001","msg":"你好"}
 * 删除好友：delete	->	{}
 * 
 * /users/${username}/contacts/
 * 获取好友：get -> {}
 *
 * /users/${username}/groups
 * 获取群组：get -> {}
 * 
 * /users/${username}/banned
 * 用户禁言:	put -> {"time":5} 
 * 取消禁言:	delete ->	{}
 * 
 *
 */
class UserRouter(val context: ActorContext)(implicit executionContext: ExecutionContext, materializer: ActorMaterializer) extends Directives with BaseRouter {
  val shardingClient = ShardClusterClient(context.system)
  val shardMediator = shardingClient.shardMediator
  val users = path("users")
  val op_contacts = path("users" / Segment / "contacts" / "users" / Segment)
  val user_contacts = path("users" / Segment / "contacts" / "users")
  val users_info = path("users" / Segment)
  val user_pwd = path("users" / Segment / "pwd")
  val user_banned = path("users" / Segment / "banned")
  val groups = path("users" / Segment / "groups")
  val gson = new Gson()

  val route: Route = {
    users {
      auth("") {
        post {
          entity(as[String]) { param =>
            ctx =>
              doCreateUser(ctx, param)
          }
        }
      }
    } ~
      users_info { (username) =>
        auth("") {
          put {
            entity(as[String]) { param =>
              ctx =>
                doModifyUserRequest(ctx, username, param)
            }
          } ~
            get {
              ctx =>
                doGetUserInfo(ctx, username)
            } ~
            delete {
              ctx =>
                doDeleteUserInfo(ctx, username)
            }
        }
      } ~
      user_pwd { (username) =>
        auth("") {
          put {
            entity(as[String]) { param =>
              ctx =>
                doResetUserPwd(ctx, username, param)
            }
          }
        }
      } ~
      user_banned { (username) =>
        auth("") {
          put {
            entity(as[String]) { param =>
              ctx =>
                doBannedUser(ctx, username, param)
            }
          } ~
            delete {
              ctx =>
                doCancelBannedUser(ctx, username)
            }
        }
      }
    op_contacts { (username: String, friendname: String) =>
      auth("") {
        post {
          entity(as[String]) { param =>
            ctx =>
              doAddUserContacts(ctx, username, friendname, param)
          }
        } ~
          delete {
            ctx =>
              doDeleteUserContacts(ctx, username, friendname)
          }
      }
    } ~
      user_contacts { username =>
        auth("") {
          get {
            ctx =>
              doGetUserContacts(ctx, username)
          }
        }
      } ~
      groups { username =>
        auth("") {
          get {
            ctx =>
              doGetUserGroups(ctx, username)
          }
        }

      } ~
      pathPrefix("users") {
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

  def doGetUserInfo(ctx: RequestContext, username: String): Future[RouteResult] = {
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
              val user = GetUserCmd(username)
              shardMediator ! shardingClient.serviceSend(user)
            case obj @ CodeResultCmd(code, msg) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(obj)))
              context stop self
            case obj @ DataResultCmd(code, data) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(obj)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doDeleteUserInfo(ctx: RequestContext, username: String): Future[RouteResult] = {
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
              val friend = DelUserCmd(username)
              shardMediator ! shardingClient.serviceSend(friend)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doModifyUserRequest(ctx: RequestContext, username: String, param: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    val json = toJson(param)
    var nickname: Option[String] = None
    var headimage: Option[String] = None
    if (json.has("nickname")) {
      val name = json.get("nickname").getAsString
      if (name != null && !name.trim().equals("")) {
        nickname = Some(name.trim())
      }
    }
    if (json.has("headimage")) {
      val image = json.get("headimage").getAsString
      if (image != null && !image.trim().equals("")) {
        headimage = Some(image.trim())
      }
    }

    if (nickname.isDefined && headimage.isDefined) {
      doModifyUser(ctx, username, nickname.get, headimage.get)
    } else if (nickname.isDefined) {
      doModifyUserNickname(ctx, username, nickname.get)
    } else if (headimage.isDefined) {
      doModifyUserHeadimage(ctx, username, headimage.get)
    } else {
      val f: Future[RouteResult] = Future {
        RouteResult.Complete(HttpResponseUtil.entityResponse(500, toJson(CodeResult(500, "param-error"))))
      }
      f
    }

  }

  def doResetUserPwd(ctx: RequestContext, username: String, param: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    implicit val timeout = Timeout(5.seconds)
    val pwd = fromSingleJson(param, classOf[NewPwd])
    val ref = context.actorOf {
      Props {
        new Actor {
          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val user = ResetUserPwdCmd(username, pwd.newpassword)
              shardMediator ! shardingClient.serviceSend(user)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doBannedUser(ctx: RequestContext, username: String, param: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    implicit val timeout = Timeout(5.seconds)
    val banned = fromSingleJson(param, classOf[BannedUser])
    val ref = context.actorOf {
      Props {
        new Actor {
          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val user = BannedUserCmd(username, banned.time)
              shardMediator ! shardingClient.serviceSend(user)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doCancelBannedUser(ctx: RequestContext, username: String): Future[RouteResult] = {
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
              val user = CancelBannedUserCmd(username)
              shardMediator ! shardingClient.serviceSend(user)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doModifyUserNickname(ctx: RequestContext, username: String, nickname: String): Future[RouteResult] = {
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
              val user = ResetUserNicknameCmd(username, nickname)
              shardMediator ! shardingClient.serviceSend(user)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]

  }

  def doModifyUserHeadimage(ctx: RequestContext, username: String, headimage: String): Future[RouteResult] = {
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
              val user = ResetUserHeadCmd(username, headimage)
              shardMediator ! shardingClient.serviceSend(user)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]

  }

  def doModifyUser(ctx: RequestContext, username: String, nickname: String, headimage: String): Future[RouteResult] = {
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
              val user = ModifyUserCmd(username, nickname, headimage)
              shardMediator ! shardingClient.serviceSend(user)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]

  }

  def doDeleteUserContacts(ctx: RequestContext, username: String, friendname: String): Future[RouteResult] = {
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
              val friend = DelUserContactCmd(username, friendname)
              shardMediator ! shardingClient.serviceSend(friend)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doAddUserContacts(ctx: RequestContext, username: String, friendname: String, param: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    implicit val timeout = Timeout(5.seconds)
    val friend = fromSingleJson(param, classOf[Friend])
    val ref = context.actorOf {
      Props {
        new Actor {

          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val addFriend = AddUserContactCmd(username, friendname, friend.msg)
              shardMediator ! shardingClient.serviceSend(addFriend)
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doGetUserContacts(ctx: RequestContext, username: String): Future[RouteResult] = {
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
              val user = GetUserContactsCmd(username)
              shardMediator ! shardingClient.serviceSend(user)
            case result @ SetResultCmd(code, set) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

  def doGetUserGroups(ctx: RequestContext, username: String): Future[RouteResult] = {
    implicit val timeout = Timeout(5.seconds)
    val ref = context.actorOf {
      Props {
        new Actor {

          var ackRef = ActorRef.noSender
          def receive = {
            case "req" =>
              ackRef = sender
              val user = GetUserGroupsCmd(username)
              shardMediator ! shardingClient.serviceSend(user)
            case result @ SetResultCmd(code, set) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
            case result @ CodeResultCmd(code: Int, msg: String) =>
              ackRef ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(result)))
              context stop self
          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]

  }

  def doCreateUser(ctx: RequestContext, param: String): Future[RouteResult] = {
    val uri = ctx.request.uri
    log.info("url[{}]", uri)
    val set = fromJson(param, classOf[User])
    implicit val timeout = Timeout(5.seconds)
    val ref = context.actorOf {
      Props {
        new Actor {
          implicit val timeout = Timeout(5.seconds)
          def receive = {
            case "req" =>
              val resultSet = scala.collection.mutable.Set.empty[CreateUserResult]
              set.foreach { user =>
                try {
                  val createUser = CreateUserCmd(user.username, user.password, user.nickname, "")
                  val future = shardMediator ? shardingClient.serviceSend(createUser)
                  val result = Await.result(future, timeout.duration).asInstanceOf[CodeResultCmd]
                  resultSet += CreateUserResult(user.username, result.code, result.msg)
                } catch {
                  case e: Exception =>
                    resultSet += CreateUserResult(user.username, 500, e.getMessage)
                }
              }
              sender ! RouteResult.Complete(HttpResponseUtil.entityResponse(toJson(resultSet.toSet)))

          }
        }
      }
    }
    ask(ref, "req").mapTo[RouteResult]
  }

}