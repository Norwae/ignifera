package com.github.norwae.ignifera

import com.typesafe.config.Config
import io.prometheus.client.{Counter, Gauge, Summary}

import scala.collection.JavaConverters._

class HttpCollectors(config: Config) {
  val requestsInFlight: Gauge = HttpCollectors.requestsInFlight
  val requestsTotal: Counter = HttpCollectors.requestsTotal
  val requestTimes: Summary = HttpCollectors.requestTimes.resolve(config)
  val responseSize: Summary = HttpCollectors.responseSize.resolve(config)
  val requestSize: Summary = HttpCollectors.requestSize.resolve(config)
}

object HttpCollectors {

  private def quantilesAndError(config: Config) =
    if (config.hasPath("quantiles.summary")) config.getDoubleList("quantiles.summary").asScala.map(_.doubleValue)
    else Seq(0.01, 0.05, 0.5, 0.95, 0.99, 0.999)

  private def quantize(summary: Summary.Builder, config: Config) = quantilesAndError(config).foldLeft(summary)((s, q) ⇒ s.quantile(q, (1 - q) / 10))

  lazy val requestsInFlight: Gauge = Gauge.
    build("http_requests_in_flight", "Requests currently in flight").
    register()

  lazy val requestsTotal: Counter = Counter.
    build("http_requests_total", "Requests processed by the application").
    labelNames("method", "code").
    register()

  lazy val requestTimes: Once[Config, Summary] = new Once(c ⇒
    quantize(Summary.
      build("http_request_duration_microseconds", "Time to response determined"), c).
      register()
  )

  lazy val responseSize: Once[Config, Summary] = new Once(c ⇒
    quantize(Summary.build("http_response_size_bytes", "Response size (estimated)"), c).
      register())

  lazy val requestSize: Once[Config, Summary] = new Once(c ⇒
    quantize(Summary.
      build("http_request_size_bytes", "Request size (estimated)"), c).
      register())
}