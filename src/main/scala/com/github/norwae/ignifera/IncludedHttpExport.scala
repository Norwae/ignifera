package com.github.norwae.ignifera

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class IncludedHttpExport extends HttpExport {
  val statusRoute: Route = encodeResponse(complete(exportReply))
}

object IncludedHttpExport extends IncludedHttpExport
