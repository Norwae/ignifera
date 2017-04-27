package com.github.norwae.ignifer

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher0, Route}
import akka.http.scaladsl.Http
import akka.stream.Materializer


class StandaloneExposition(port: Int, interface: String = "0.0.0.0", observedPath: PathMatcher0 = "/status" )(implicit system: ActorSystem, mat: Materializer) extends Exposition {
  private val route: Route = {
    get {
      path(observedPath) {
???
      }
    }
  }
  Http().bindAndHandle(route, interface, port)

}
