import WebKeys._
import JsEngineKeys._

lazy val root = (project in file("."))
  .settings(
    name := "tim-protocol-tools",
    organization := "com.tsingb.tim",

    git.baseVersion := "1.0",

    resolvers += Resolver.bintrayRepo("buddho", "mvn-public"),

    scalaVersion := "2.11.7",
    scalacOptions ++= Seq("-deprecation","-target:jvm-1.8"),

    //scalacOptions += "-target:jvm-1.8",

    //javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),

    pipelineStages := Seq(concat, uglify),


    libraryDependencies ++=
      Dependencies.akka ++
      Dependencies.commonStack ++
      Dependencies.tsingb ++
      Dependencies.others,

    // can not run tests in parallel because of in memory H2 database
    parallelExecution in Test := false,

    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),

    initialize := {
      val _ = initialize.value
      if (sys.props("java.specification.version") != "1.8") {
        sys.error("Java 8 is required for this project.")
      }
    }


  )

  //retrieveManaged := true
