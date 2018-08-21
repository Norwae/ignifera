package com.github.norwae.ignifera

import akka.http.scaladsl.model.{HttpMethod, StatusCode, Uri}

import scala.concurrent.duration.FiniteDuration

/**
  * Implementations of this trait can be registered with a [[StatsCollector]]
  * to receive notifications about received requests and computed responses.
  */
trait HttpEventListener {
  /** called when a request is forwarded to application logic */
  def onRequestStart(): Unit

  /**
    * called when a request is completed, and the response completed
    * @param requestMethod method
    * @param requestUri request URI
    * @param requestSize request size, if possible to cheaply determine
    * @param responseCode response code computed
    * @param responseSize response size, if possible to cheaply determine
    * @param duration request duration
    */
  def onRequestEnd(requestMethod: HttpMethod, requestUri: Uri, requestSize: Option[Long],
                   responseCode: StatusCode, responseSize: Option[Long],
                   duration: FiniteDuration): Unit
}
