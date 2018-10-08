import sbt.Keys.description

inThisBuild(Seq(
  organization := "com.github.norwae",
  version := "1.1.1-SNAPSHOT",
  scalaVersion := "2.12.6",
))

lazy val bench = project in file("benchmarks") settings(
  name := "ignifera-benchmarks"
) enablePlugins JmhPlugin dependsOn main

lazy val main = project in file(".") settings(
  name := "ignifera",
  description :=
    """
      |Adds deployment support for akka-http applications in kubernetes.
      |The facilities are:
      |
      |1) promotheus statistics export and collection for
      |akka http routes. The library collects http result codes,
      |timings, and requests in flight. It additionally optionally
      |exposes some basic akka statistics.
      |
      |2) Graceful shutdown, health and readiness functions. These routes
      |are provided at a low level to they can be used by both the routing
      |DSL and special case implementations.
      |
      |3) Access log collection.
      |
      |These functions can be freely composed to (e.g.) exclude health check
      |routes from statistics and access log, or include them, depending
      |on the requirements and standards of the user""".stripMargin,
  scalacOptions := Seq("-deprecation"),
  crossScalaVersions := Seq("2.11.8"),
  publishMavenStyle := true,
  libraryDependencies ++= {
    val akkaVersion = "2.5.14"
    val prometheusVersion = "0.5.0"
    val akkaHttpVersion = "10.1.3"

    Seq(
      // the actor stuff
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,

      // and the prometheus stuff
      "io.prometheus" % "simpleclient" % prometheusVersion,
      "io.prometheus" % "simpleclient_hotspot" % prometheusVersion,
      "io.prometheus" % "simpleclient_common" % prometheusVersion,
      "io.prometheus" % "simpleclient_pushgateway" % prometheusVersion,

      // slf4j for access logs
      "org.slf4j" % "slf4j-api" % "1.7.25",

      // testing dependencies
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "org.mockito" %% "mockito-scala" % "0.4.5" % "test"
    )
  },
  scmInfo := Some(ScmInfo(url("https://github.com/norwae/ignifera"), "scm:git:https://github.com/Norwae/ignifera.git", Some("scm:git:ssh://git@github.com:Norwae/ignifera.git"))),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra :=
    Seq(<licenses>
      <license>
        <name>BSD 2-Clause</name>
        <url>https://github.com/Norwae/oriana/blob/master/LICENSE</url>
        <distribution>repo</distribution>
      </license>
    </licenses>, <developers>
      <developer>
        <name>Stefan Schulz</name>
        <email>schulz.stefan@gmail.com</email>
      </developer>
    </developers>, <url>https://github.com/norwae/ignifera</url>)
)

