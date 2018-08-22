package com.github.norwae.ignifera

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher0, Route}
import akka.stream.Materializer

import scala.concurrent.Future


/**
  * Launches an additional akka http stack to provide the status route
  *
  * @param port         port to bind (use 0 for auto-allocation)
  * @param interface    interface to listen on. Default is exposed to the world
  * @param observedPath path to listen on. Default is "metrics"
  * @param extensions   additional routes to make available on the metrics endpoint. Default is None
  * @param system       actor system
  * @param mat          materializer
  */
class StandaloneExport(port: Int, interface: String = "0.0.0.0", observedPath: PathMatcher0 = "metrics", extensions: Option[Route] = None)(implicit system: ActorSystem, mat: Materializer) {
  private val route: Route = {
    val base =
      path(observedPath) {
        get {
          IncludedHttpExport.statusRoute
        }
      }

    Route seal (extensions map base.~ getOrElse base)
  }

  /**
    * Binds the specified port, and starts listening for status requests
    *
    * @return server binding object
    */
  def start(): Future[Http.ServerBinding] = Http().bindAndHandle(route, interface, port)

}
