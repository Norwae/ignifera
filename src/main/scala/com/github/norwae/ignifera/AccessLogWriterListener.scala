package com.github.norwae.ignifera
import akka.http.scaladsl.model.{HttpMethod, StatusCode, Uri}
import org.slf4j.Logger

import scala.concurrent.duration.FiniteDuration

/**
  * Writes access logs as formatted json. The messages have the following format:
  *
  *
  * @param logger SLF4J logger which will be used to receive the log messages
  */
class AccessLogWriterListener(logger: Logger) extends HttpEventListener {
  /** Does nothing */
  override def onRequestStart(): Unit = ()

  /** logs an `info` level message with the following body:
    * {{{
    * {
    * "requestMethod": "GET",
    * "requestUri": "/hello",
    * "requestSize": 0,
    * "responseSize": null,
    * "duration": 192
    * }
    * }}}
    * @param requestMethod method
    * @param requestUri request URI
    * @param requestSize request size, if possible to cheaply determine
    * @param responseCode response code computed
    * @param responseSize response size, if possible to cheaply determine
    * @param duration request duration
    */
  override def onRequestEnd(requestMethod: HttpMethod, requestUri: Uri, requestSize: Option[Long],
                            responseCode: StatusCode, responseSize: Option[Long],
                            duration: FiniteDuration): Unit = {
    val json = s"""
       |{
       |  "requestMethod": "$requestMethod",
       |  "requestUri": "$requestUri",
       |  "requestSize": ${requestSize.map(_.toString).getOrElse("null")},
       |  "responseSize": ${requestSize.map(_.toString).getOrElse("null")},
       |  "duration": ${duration.toMillis}
       |}
     """.stripMargin
    logger.info(json)
  }
}
