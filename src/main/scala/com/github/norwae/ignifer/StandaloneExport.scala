package com.github.norwae.ignifer

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher0, Route}
import akka.http.scaladsl.Http
import akka.stream.Materializer


class StandaloneExport(port: Int, interface: String = "0.0.0.0", observedPath: PathMatcher0 = "status" )(implicit system: ActorSystem, mat: Materializer) {
  private val route: Route = Route seal {
    get {
      path(observedPath) {
          IncludedHttpExport.statusRoute
      }
    }
  }

  def start() = Http().bindAndHandle(route, interface, port)

}
