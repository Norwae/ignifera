package com.github.norwae.ignifera

import com.typesafe.config.Config
import io.prometheus.client.{Counter, Gauge, Summary}
import scala.collection.JavaConverters._

class HttpCollectors(config: Config) {
  private val quantilesAndError =
    if (config.hasPath("quantiles.summary")) config.getDoubleList("quantiles.summary").asScala.map(_.doubleValue)
    else Seq(0.01, 0.05, 0.5, 0.95, 0.99, 0.999)

  private def quantize(summary: Summary.Builder) = quantilesAndError.foldLeft(summary)((s, q) ⇒ s.quantile(q,  (1 - q) / 10))

  val requestsInFlight: Gauge = Gauge.
    build("http_requests_in_flight", "Requests currently in flight").
    register()
  val requestsTotal: Counter = Counter.
    build("http_requests_total", "Requests processed by the application").
    labelNames("method", "status").
    register()
  val requestTimes: Summary = quantize(Summary.
    build("http_request_duration_microseconds", "Time to response determined")).
    register()
  val responseSize: Summary = quantize(Summary.
    build("http_response_size_bytes", "Response size (estimated)")).
    register()
  val requestSize: Summary = quantize(Summary.
    build("http_request_size_bytes", "Request size (estimated)")).
    register()
}
