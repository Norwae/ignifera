package com.github.norwae.ignifer

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{BidiFlow, Flow, Keep}

/**
  * Allows to wrap a handler flow (as usually passed to
  * [[akka.http.scaladsl.HttpExt.bindAndHandle]] inside a
  * stats collector that automatically collects prometheus metrics.
  */
object StatsCollector {
  def apply[A](flow: Flow[HttpRequest, HttpResponse, A]): Flow[HttpRequest, HttpResponse, A] =
    BidiFlow.fromGraph(new StatsCollectorStage).joinMat(flow)(Keep.right)

}
