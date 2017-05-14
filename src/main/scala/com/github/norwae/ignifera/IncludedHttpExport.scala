package com.github.norwae.ignifera

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

/**
  * Provides a route to be included in an existing akka http stack. This is a low-impact
  * method of including ignifera. However, in case of overload of the business logic,
  * status requests will be throttled by the same backpressure mechanism as application
  * logic requests. For this reason, generally a standalone deployment is preferable.
  */
trait IncludedHttpExport extends HttpExport {
  val statusRoute: Route = encodeResponse(complete(exportReply))
}

object IncludedHttpExport extends IncludedHttpExport
