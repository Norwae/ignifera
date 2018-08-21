package com.github.norwae.ignifera
import akka.http.scaladsl.model.{HttpMethod, StatusCode, Uri}
import io.prometheus.client.Summary

import scala.concurrent.duration.FiniteDuration

class PrometheusStatisticsListener(collectors: HttpCollectors) extends HttpEventListener {
  import collectors._
  override def onRequestStart(): Unit = {
    requestsInFlight.inc()
  }

  private def observeSize(summary: Summary, bytes: Option[Long]): Unit =
    bytes.foreach(b ⇒ summary.observe(b.toDouble))

  override def onRequestEnd(requestMethod: HttpMethod, requestUri: Uri, requestBytes: Option[Long],
                            responseCode: StatusCode, responseBytes: Option[Long],
                            duration: FiniteDuration): Unit = {
    requestsInFlight.dec()
    requestTimes.observe(duration.toMillis)

    observeSize(requestSize, requestBytes)
    observeSize(responseSize, responseBytes)
    collectors.requestsTotal.labels(requestMethod.value, responseCode.intValue().toString).inc()
    collectors.requestTimes.observe(duration.toMicros)
  }
}
