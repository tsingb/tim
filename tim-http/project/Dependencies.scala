import sbt._

object Dependencies {

  object V {
    val slf4j = "1.7.16"
    val akka = "2.4.2"
    val jedis = "2.8.0"
    val tsingb = "1.0.0"

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
      "commons-io" % "commons-io" % "2.4",
      "com.github.scullxbones" %% "akka-persistence-mongo-casbah" % "1.2.0",
      "org.mongodb" % "casbah-core_2.11" % "3.1.0",
      "commons-codec" % "commons-codec" % "1.9",
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.5",
      "org.apache.logging.log4j" % "log4j-api" % "2.5",
      "org.apache.logging.log4j" % "log4j-core" % "2.5",
      "com.google.code.gson" % "gson" % "2.6.1"
    )

   val tsingb = Seq(
      "com.tsingb.tim" %% "tim-event" % V.tsingb,
      "com.tsingb.tim" %% "tim-cluster" % V.tsingb
    )

}