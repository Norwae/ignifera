package com.github.norwae.ignifer

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}
import io.prometheus.client.{Counter, Gauge}

class StatsCollectorStage extends GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]]{
  private val inboundRequest = Inlet[HttpRequest]("rq-in")
  private val outboundRequest = Outlet[HttpRequest]("rq-out")
  private val inboundResponse = Inlet[HttpResponse]("rp-in")
  private val outboundResponse = Outlet[HttpResponse]("rp-out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    import StatsCollectorStage._
    val requestForward = new InHandler with OutHandler {
      override def onPush(): Unit = {
        val request = grab(inboundRequest)
        requestsInFlight.inc()
        requestsTotal.labels(request.method.value).inc()

        push(outboundRequest, request)
      }

      override def onPull(): Unit = pull(inboundRequest)
    }

    val responseForward = new InHandler with OutHandler {
      override def onPush(): Unit = {
        val response = grab(inboundResponse)
        requestsInFlight.dec()
        responsesTotal.labels(response.status.value).inc()
        push(outboundResponse, response)
      }

      override def onPull(): Unit = pull(inboundResponse)
    }

    setHandler(inboundRequest, requestForward)
    setHandler(outboundRequest, requestForward)
    setHandler(inboundResponse, responseForward)
    setHandler(outboundResponse, responseForward)
  }

  override def shape: BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse] =
    BidiShape(inboundRequest, outboundRequest, inboundResponse, outboundResponse)
}

object StatsCollectorStage {
  private val requestsInFlight = Gauge.
    build("http_requests_in_flight", "Requests currently in flight").
    register()
  private val requestsTotal = Counter.
    build("http_requests_total", "Requests received by the application").
    labelNames("method").
    register()
  private val responsesTotal = Counter.
    build("http_responses_total", "Responses issued by the application").
    labelNames("status").
    register()

}