package com.tsingb.tim.shard.exception

object UserNotFoundException {
  def apply(message: String, cause: Throwable): UserNotFoundException =
    (new UserNotFoundException1(message, cause)).asInstanceOf[UserNotFoundException]

  def apply(message: String): UserNotFoundException =
    (new UserNotFoundException2(message)).asInstanceOf[UserNotFoundException]

  def apply(cause: Throwable): UserNotFoundException =
    (new UserNotFoundException3(cause)).asInstanceOf[UserNotFoundException]

  def apply(): UserNotFoundException =
    (new UserNotFoundException4).asInstanceOf[UserNotFoundException]
}

trait UserNotFoundException extends RuntimeException

class UserNotFoundException1(message: String, cause: Throwable)
  extends RuntimeException(message, cause) with UserNotFoundException

class UserNotFoundException2(message: String)
  extends RuntimeException(message) with UserNotFoundException

class UserNotFoundException3(cause: Throwable)
  extends RuntimeException(cause) with UserNotFoundException

class UserNotFoundException4 extends RuntimeException with UserNotFoundException  