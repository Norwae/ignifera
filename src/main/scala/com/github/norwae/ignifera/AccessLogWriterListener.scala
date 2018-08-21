package com.github.norwae.ignifera
import akka.http.scaladsl.model.{HttpMethod, StatusCode, Uri}
import org.slf4j.Logger

import scala.concurrent.duration.FiniteDuration

class AccessLogWriterListener(logger: Logger) extends HttpEventListener {
  override def onRequestStart(): Unit = ()

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
