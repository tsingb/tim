package com.tsingb.tim.shard.service

import java.util.UUID
import java.util.regex.Pattern
import scala.collection.JavaConversions.asScalaBuffer
import com.mongodb.ServerAddress
import com.mongodb.casbah.Imports.$set
import com.mongodb.casbah.Imports.DBObject
import com.mongodb.casbah.Imports.LongDoNOk
import com.mongodb.casbah.Imports.MongoCursor
import com.mongodb.casbah.Imports.mongoQueryStatements
import com.mongodb.casbah.Imports.wrapDBObj
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.commons.MongoDBList
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.config.ConfigFactory
import com.tsingb.tim.data._
import com.tsingb.tim.event._
import com.google.gson.Gson
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ActorSystem
import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.Props
import com.tsingb.tim.cmd.SetResultCmd
import com.tsingb.tim.cmd.CodeResultCmd
import com.tsingb.tim.shard.exception.UserNotFoundException
import com.tsingb.tim.cmd.DataResultCmd

object MongoService extends ExtensionId[MongoServiceExt] with ExtensionIdProvider {
  override def get(system: ActorSystem) = super.get(system)
  override def lookup = MongoService
  override def createExtension(system: ExtendedActorSystem) = new MongoServiceExt(system)

  case class SaveUserMessage(userName: String, msg: UserMessage)
  case class SaveGroupMessage(msg: GroupMessage)
  case class SaveGroup(group: GroupData)
  case class SaveUser(user: UserData)
  case class RemoveUser(userName: String)
  case class GetUsers(users: Set[String])
  case class GetUserNickName(userName: String)
  case class DeleteUser(userName: String)
  case class UpdateUserNickname(userName: String, nickName: String)
  case class UpdateUserHeadImage(userName: String, headImage: String)
  case class UpdateUser(userName: String, nickName: String, headImage: String)
  case class UserAddContact(userName: String, friendName: String)
  case class UserDelContact(userName: String, friendName: String)
  case class GetGroups(groupIds: Set[String])
}

protected class MongoServiceExt(system: ExtendedActorSystem) extends Extension with Serializable {

  private val typeName = "mongo-service"

  def start = {
    SeviceExtension(system).start(typeName, Some(Props(classOf[MongoServiceActor])))
  }

  def region = SeviceExtension(system).serviceRegion(typeName)

}

protected class MongoServiceActor extends AbdtractServiceActor with MongoCombinService {

  import MongoService._

  def receive = {
    case SaveUserMessage(userName, msg) =>
      insertUserMessage(userName, msg)
    case SaveGroupMessage(msg) =>
      insertGroupMessage(msg)
    case SaveGroup(group) =>
      insertGroup(group)
    case SaveUser(user) =>
      insertUser(user)
    case RemoveUser(userName) =>
      deleteUser(userName)
    case UpdateUserNickname(userName, nickName) =>
      resetUserNickname(userName, nickName)
    case UpdateUserHeadImage(userName, headImage) =>
      resetUserHeadImage(userName, headImage)
    case UpdateUser(userName, nickName, headImage) =>
      updateUser(userName, nickName, headImage)
    case UserAddContact(userName, friendName) =>
      insertContact(userName, friendName)
    case UserDelContact(userName, friendName) =>
      deleteContact(userName, friendName)
    case DeleteUser(userName) =>
      deleteUser(userName)
    case GetUsers(users) =>
      try {
        val set = this.getUserDatas(users)
        sender ! SetResultCmd(200, set)
      } catch {
        case t: Throwable =>
          sender ! CodeResultCmd(500, "query error")
      }
    case GetUserNickName(userName) =>
      try {
        val nickName = getUserNickName(userName)
        sender ! DataResultCmd(200, StringData(nickName))
      } catch {
        case e: UserNotFoundException =>
          sender ! CodeResultCmd(404, "query error")
        case t: Throwable =>
          sender ! CodeResultCmd(500, "query error")
      }
    case GetGroups(groupIds) =>
      try {
        val set = queryGroupById(groupIds)
        sender ! SetResultCmd(200, set)
      } catch {
        case t: Throwable =>
          sender ! CodeResultCmd(500, "query error")
      }
    case x =>
  }

}

protected trait MongoCombinService extends MessageMongoService with UserMongoService with EventMongoService with GroupMongoService {

}

protected trait GroupMongoService extends MongoConfig {
  def insertGroup(group: GroupData) {
    val coll = table(group_info)
    val builder = MongoDBObject.newBuilder
    builder += "group_id" -> group.groupId
    builder += "group_name" -> group.groupName
    builder += "group_desc" -> group.desc
    builder += "group_owner" -> group.owner
    builder += "max_users" -> group.maxUsers
    builder += "public" -> group.public
    builder += "create_time" -> group.createTime
    builder += "modify_time" -> group.modifyTime
    builder += "status" -> 1
    coll.insert(builder.result())
  }

  def deleteGroup(groupId: String) {
    val coll = table(group_info)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("group_id" -> groupId)).update($set("status" -> -1, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  def queryGroupById(groupIds: Set[String]): Set[GroupData] = {
    val coll = table(group_info)
    val builder = MongoDBObject.newBuilder
    builder += "status" -> 1
    val queryObject = builder.result()
    queryObject.putAll("group_id" $in groupIds.toArray)
    var cur = coll.find(queryObject)
    val set = scala.collection.mutable.Set.empty[GroupData]
    cur.foreach(obj => {
      val groupId = obj.getAs[String]("group_id").get
      val groupName = obj.getAs[String]("group_name").get
      val desc = obj.getAs[String]("desc").get
      val owner = obj.getAs[String]("group_owner").get
      val maxUsers = obj.getAs[Int]("max_users").get
      val public = obj.getAs[Boolean]("public").get
      val createTime = obj.getAs[Long]("create_time").get
      val modifyTime = obj.getAs[Long]("modify_time").get
      val group = GroupData(groupId, groupName, owner, desc, public, maxUsers, createTime, modifyTime, true)
      //      val group = UserGroup(groupId, groupName)
      set += group
    })
    set.toSet
  }

  def queryGroupByName(groupname: String, exclusion: Set[String]): Set[GroupData] = {
    val coll = table(group_info)
    val builder = MongoDBObject.newBuilder
    builder += "status" -> 1
    builder += "public" -> true
    val queryObject = builder.result()
    queryObject.putAll("group_name" $regex groupname)
    queryObject.putAll("group_id" $nin exclusion.toArray)
    var cur = coll.find(queryObject).limit(20)
    val set = scala.collection.mutable.Set.empty[GroupData]
    cur.foreach(obj => {
      val groupId = obj.getAs[String]("group_id").get
      val groupName = obj.getAs[String]("group_name").get
      //      val group = UserGroup(groupId, groupName)
      //      set += group
    })
    set.toSet
  }
}

protected trait UserMongoService extends MongoConfig {

  /**
   * 新增用户
   */
  def insertUser(user: UserData) {
    val coll = table(user_info)
    val builder = MongoDBObject.newBuilder
    builder += "user_name" -> user.userName
    builder += "nick_name" -> user.nickName
    builder += "head_image" -> user.nickName
    builder += "create_time" -> user.createTime
    builder += "modify_time" -> user.modifyTime
    builder += "status" -> 1
    coll.insert(builder.result())
  }

  def insertContact(userName: String, friendName: String) {
    val time = System.currentTimeMillis()
    val coll = table(user_contact)
    val builder = MongoDBObject.newBuilder
    builder += "user_name" -> userName
    builder += "friend_name" -> friendName
    builder += "create_time" -> time
    builder += "modify_time" -> time
    builder += "status" -> 1
    coll.insert(builder.result())
  }

  /**
   * 删除用户
   */
  def deleteUser(userName: String) {
    val coll = table(user_info)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("user_name" -> userName)).update($set("status" -> -1, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  def deleteContact(userName: String, friendName: String) {
    val coll = table(user_info)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("user_name" -> userName, "friend_name" -> friendName)).update($set("status" -> -1, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  /**
   * 重置密码
   */
  def resetUserPassword(userName: String, password: String) {
    val coll = table(user_info)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("user_name" -> userName)).update($set("password" -> password, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  def resetUserNickname(userName: String, nickname: String) {
    val coll = table(user_info)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("user_name" -> userName)).update($set("nick_name" -> nickname, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  def resetUserHeadImage(userName: String, headImage: String) {
    val coll = table(user_info)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("user_name" -> userName)).update($set("head_image" -> headImage, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  def updateUser(userName: String, nickname: String, headImage: String) {
    val coll = table(user_info)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("user_name" -> userName)).update($set("nick_name" -> nickname, "head_image" -> headImage, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  def getUserNickName(userName: String): String = {
    val coll = table(user_info)
    val builder = MongoDBObject.newBuilder
    builder += "user_name" -> userName
    val queryObject = builder.result()
    var objOption = coll.findOne(queryObject, MongoDBObject("nick_name" -> 1))
    if (objOption.isDefined) {
      objOption.get.getAs[String]("nick_name").get
    } else {
      throw UserNotFoundException()
    }
  }

  def getUserDatas(userNames: Set[String]): Set[UserData] = {
    val coll = table(user_info)
    val builder = MongoDBObject.newBuilder
    builder += "status" -> 1
    val queryObject = builder.result()
    queryObject.putAll("user_name" $in userNames.toArray)
    var cur = coll.find(queryObject)
    val set = scala.collection.mutable.Set.empty[UserData]
    cur.foreach(obj => {
      val userName = obj.getAs[String]("user_name").get
      val nickName = obj.getAs[String]("nick_name").get
      val headImage = obj.getAs[String]("head_image").get
      val user = UserData("", userName, nickName, headImage, 0, 0, true)
      set += user
    })
    set.toSet
  }

}

protected trait EventMongoService extends MongoConfig {
  private val gson = new Gson()

  def updateEventStatus(eventId: String, status: Int) {
    val coll = table(user_event)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("evt_id" -> eventId)).update($set("status" -> status, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  def insertClientEvent(userName: String, evt: Event) {
    val coll = table(user_event)
    val builder = MongoDBObject.newBuilder
    builder += "user_name" -> userName
    builder += "type" -> evt.getClass.getName
    builder += "evt_id" -> evt.eventId
    builder += "evt" -> gson.toJson(evt)
    builder += "status" -> 0
    builder += "modify_time" -> System.currentTimeMillis()
    coll.insert(builder.result())
  }

  def queryUserEventById(userName: String, eventIds: Set[String]): Set[Event] = {
    val coll = table(user_event)
    val builder = MongoDBObject.newBuilder
    builder += "user_name" -> userName
    builder += "status" -> 0
    val queryObject = builder.result()
    queryObject.putAll("evt_id" $in eventIds.toArray)
    var cur = coll.find(queryObject)
    val set = scala.collection.mutable.Set.empty[Event]
    cur.foreach(obj => {
      val eventType = obj.getAs[String]("type").get
      val evt = obj.getAs[String]("evt").get
      set += gson.fromJson(evt, Class.forName(eventType)).asInstanceOf[Event]
    })
    set.toSet
  }
}

protected trait MessageMongoService extends MongoConfig {

  def insertUserMessage(userName: String, msg: UserMessage) {
    val coll = table(user_message)
    val builder = MongoDBObject.newBuilder
    builder += "user_name" -> userName
    builder += "from" -> msg.from
    builder += "to" -> msg.to
    builder += "msg_id" -> msg.msgId
    builder += "body" -> msg.body
    builder += "ext" -> msg.ext
    builder += "modal" -> msg.modal
    builder += "time" -> msg.time
    builder += "modify_time" -> System.currentTimeMillis()
    builder += "status" -> 0
    coll.insert(builder.result())
  }

  def insertGroupMessage(msg: GroupMessage) {
    val coll = table(group_message)
    val builder = MongoDBObject.newBuilder
    builder += "group_id" -> msg.to
    builder += "from" -> msg.from
    builder += "to" -> msg.to
    builder += "msg_id" -> msg.msgId
    builder += "body" -> msg.body
    builder += "ext" -> msg.ext
    builder += "time" -> msg.time
    builder += "modify_time" -> System.currentTimeMillis()
    builder += "status" -> 0
    coll.insert(builder.result())
  }

  def updateUserMessageReceived(userName: String, msgId: String) {
    val coll = table(user_message)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("user_name" -> userName)).update($set("status" -> 1, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  def updateUserMessageReaded(userName: String, msgId: String) {
    val coll = table(user_message)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("user_name" -> userName)).update($set("status" -> 2, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

  def deleteUserMessage(userName: String, msgId: String) {
    val coll = table(user_message)
    val builder = coll.initializeOrderedBulkOperation
    builder.find(MongoDBObject("user_name" -> userName)).update($set("status" -> -1, "modify_time" -> System.currentTimeMillis()))
    val result = builder.execute
  }

}

protected trait MongoConfig {
  private val conf = ConfigFactory.load("mongo.conf")
  val hosts = conf.getStringList("mongo.host")
  var addArr = scala.collection.mutable.Set.empty[ServerAddress]

  def user_info = conf.getString("mongo.collections.user_info_collection")
  def user_contact = conf.getString("mongo.collections.user_contact_collection")
  def group_info = conf.getString("mongo.collections.group_info_collection")
  def group_member_info = conf.getString("mongo.collections.group_member_info_collection")
  def user_message = conf.getString("mongo.collections.user_message_collection")
  def group_message = conf.getString("mongo.collections.group_message_collection")
  def user_event = conf.getString("mongo.collections.user_event_collection")
  def user_channel = conf.getString("mongo.collections.user_channel_collection")

  hosts.foreach(hostStr => {
    val arr = hostStr.split(":")
    val host = arr(0)
    val port = arr(1).toInt
    addArr += (new ServerAddress(host, port))
  })

  private[this] val mongoClient = MongoClient(addArr.toList)
  private[this] val dbname = conf.getString("mongo.db")
  def db: MongoDB = {
    val db = mongoClient(dbname)
    db
  }

  def db(dbname: String): MongoDB = {
    val db = mongoClient(dbname)
    db
  }

  def table(collName: String): MongoCollection = {
    db(collName)
  }

}