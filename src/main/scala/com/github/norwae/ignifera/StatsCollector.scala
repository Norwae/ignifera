package com.github.norwae.ignifera

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{BidiFlow, Flow, Keep}
import com.typesafe.config.{Config, ConfigFactory}

/**
  * Allows to wrap a handler flow (as usually passed to
  * [[akka.http.scaladsl.HttpExt.bindAndHandle]] inside a
  * stats collector that automatically collects prometheus metrics.
  */
object StatsCollector {
  def apply[A](flow: Flow[HttpRequest, HttpResponse, A]): Flow[HttpRequest, HttpResponse, A] =
    apply(flow, new HttpCollectors(ConfigFactory.empty()))

  def apply[A](flow: Flow[HttpRequest, HttpResponse, A], collectors: HttpCollectors): Flow[HttpRequest, HttpResponse, A] =
    apply(flow, new PrometheusStatisticsListener(collectors))

  def apply[A](flow: Flow[HttpRequest, HttpResponse, A], listeners: HttpEventListener*): Flow[HttpRequest, HttpResponse, A] =
    BidiFlow.
      fromGraph(new StatsCollectorStage(listeners)).
      joinMat(flow)(Keep.right)


}
