package com.github.norwae.ignifer

import akka.http.scaladsl.model.{HttpMethod, HttpRequest, HttpResponse}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}
import io.prometheus.client.{Counter, Gauge, Histogram}

class StatsCollectorStage extends GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]]{
  private val inboundRequest = Inlet[HttpRequest]("rq-in")
  private val outboundRequest = Outlet[HttpRequest]("rq-out")
  private val inboundResponse = Inlet[HttpResponse]("rp-in")
  private val outboundResponse = Outlet[HttpResponse]("rp-out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    import StatsCollectorStage._

    var inFlightData = Vector.empty[(Histogram.Timer, HttpMethod)]
    val requestForward = new InHandler with OutHandler {
      override def onPush(): Unit = {
        val request = grab(inboundRequest)
        requestsInFlight.inc()
        inFlightData = inFlightData :+ (requestTimes.startTimer(), request.method)

        push(outboundRequest, request)
      }

      override def onPull(): Unit = pull(inboundRequest)
    }

    val responseForward = new InHandler with OutHandler {
      override def onPush(): Unit = {
        val response = grab(inboundResponse)
        val (start, method) = inFlightData.head
        inFlightData = inFlightData.tail
        requestsInFlight.dec()
        responsesTotal.labels(method.value, response.status.value).inc()
        start.close()
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
  private val responsesTotal = Counter.
    build("http_responses_total", "Responses issued by the application").
    labelNames("method", "status").
    register()
  private val requestTimes = Histogram.
    build("http_request_time", "Time to response determined").
    buckets(0.005,0.01,0.025,0.05,0.075,0.1,0.25,0.5,0.75,1,2.5,5,7.5,10).
    register()


}