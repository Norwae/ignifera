package com.github.norwae.ignifer

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object IncludedHttpExport$ extends HttpExport {
  val statusRoute: Route = encodeResponse(complete(exportReply))
}
