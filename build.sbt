organization := "com.github.norwae"

name := "ignifera"

description := """
  |Adds promotheus statistics export and collection for
  |akka http routes. The library collects http result codes,
  |timings, and requests in flight. It additionally optionally
  |exposes some basic akka statistics.""".stripMargin

version := "1.0.2-SNAPSHOT"

scalaVersion := "2.12.3"

scalacOptions := Seq("-deprecation")

crossScalaVersions := Seq("2.11.8")

publishMavenStyle := true


libraryDependencies ++= {
  val akkaVersion = "2.5.4"
  val prometheusVersion = "0.0.21"
  val akkaHttpVersion = "10.0.9"

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

    // testing dependencies
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  )
}

scmInfo := Some(ScmInfo(url("https://github.com/norwae/ignifera"), "scm:git:https://github.com/Norwae/ignifera.git", Some("scm:git:ssh://git@github.com:Norwae/ignifera.git")))

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