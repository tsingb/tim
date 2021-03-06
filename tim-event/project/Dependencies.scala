import sbt._

object Dependencies {

  object V {
    val slf4j = "1.7.16"
    val akka = "2.4.2"
    val jedis = "2.8.0"

  }

  // config

  val jedis = "redis.clients" % "jedis" % V.jedis

   val akka = Seq(
      "com.typesafe.akka" %% "akka-actor" % V.akka,
      "com.typesafe.akka" %% "akka-agent" % V.akka,
      "com.typesafe.akka" %% "akka-cluster" % V.akka,
      "com.typesafe.akka" %% "akka-cluster-sharding" % V.akka,
      "com.typesafe.akka" %% "akka-cluster-metrics" % V.akka,
      "com.typesafe.akka" %% "akka-remote" % V.akka,
      "com.typesafe.akka" %% "akka-persistence" % V.akka,
      "com.typesafe.akka" %% "akka-contrib" % V.akka,
      "com.typesafe.akka" %% "akka-slf4j" % V.akka,
      "com.typesafe.akka" %% "akka-http-experimental" % V.akka
    )


  // common

  val java8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % "0.7.0"

  val commonStack = Seq(java8Compat,jedis)

   val others = Seq(
      "com.google.code.gson" % "gson" % "2.6.1"
    )

}