package com.github.norwae.ignifera

import akka.http.scaladsl.model.{HttpMethod, StatusCode, Uri}

import scala.concurrent.duration.FiniteDuration

trait HttpEventListener {
  def onRequestStart(): Unit
  def onRequestEnd(requestMethod: HttpMethod, requestUri: Uri, requestSize: Option[Long],
                   responseCode: StatusCode, responseSize: Option[Long],
                   duration: FiniteDuration): Unit
}
