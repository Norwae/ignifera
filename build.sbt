organization := "com.github.norwae"
name := "ignifera"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.12.2"
crossScalaVersions := Seq("2.11.8")

val akkaVersion = "2.4.17"
val prometheusVersion = "0.0.21"
val akkaHttpVersion = "10.0.5"

libraryDependencies ++= Seq(
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