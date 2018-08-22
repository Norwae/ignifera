package com.github.norwae.ignifera

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{BidiFlow, Flow, Keep}
import com.typesafe.config.ConfigFactory

/**
  * Allows to wrap a handler flow (as usually passed to
  * [[akka.http.scaladsl.HttpExt.bindAndHandle]] inside a
  * stats collector that automatically collects prometheus metrics.
  */
object StatsCollector {
  /**
    * creates a stats collector reporting to [[PrometheusStatisticsListener]] with default config
    *
    * @param flow application flow
    * @tparam A materialized value
    * @return wrapped flow
    */
  def apply[A](flow: Flow[HttpRequest, HttpResponse, A]): Flow[HttpRequest, HttpResponse, A] =
    apply(flow, new HttpCollectors(ConfigFactory.empty()))

  /**
    * creates a stats collector reporting to [[PrometheusStatisticsListener]] with the provided collectors
    *
    * @param flow       application flow
    * @param collectors http collector
    * @tparam A materialized value
    * @return wrapped flow
    */
  def apply[A](flow: Flow[HttpRequest, HttpResponse, A], collectors: HttpCollectors): Flow[HttpRequest, HttpResponse, A] =
    apply(flow, new PrometheusStatisticsListener(collectors))

  /**
    * creates a stats collector reporting to the specified listeners
    *
    * @param flow      application flow
    * @param listeners listeners to inform on requests
    * @tparam A materialized value
    * @return wrapped flow
    */
  def apply[A](flow: Flow[HttpRequest, HttpResponse, A], listeners: HttpEventListener*): Flow[HttpRequest, HttpResponse, A] =
    BidiFlow.
      fromGraph(new StatsCollectorStage(listeners)).
      joinMat(flow)(Keep.right)


}
