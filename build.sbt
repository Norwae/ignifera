import sbt.Keys.description

inThisBuild(Seq(
  organization := "com.github.norwae",
  version := "1.0.3-SNAPSHOT",
  scalaVersion := "2.12.6",
))

lazy val bench = project in file("benchmarks") settings(
  name := "ignifera-benchmarks"
) enablePlugins JmhPlugin

lazy val main = project in file(".") settings(
  name := "ignifera",
  description :=
    """
      |Adds promotheus statistics export and collection for
      |akka http routes. The library collects http result codes,
      |timings, and requests in flight. It additionally optionally
      |exposes some basic akka statistics.""".stripMargin,
  scalacOptions := Seq("-deprecation"),
  crossScalaVersions := Seq("2.11.8"),
  publishMavenStyle := true,
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

