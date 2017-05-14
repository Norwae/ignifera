package com.github.norwae.ignifera

import akka.http.scaladsl.model.{HttpMessage, HttpRequest, HttpResponse}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}
import io.prometheus.client.{Counter, Gauge, Summary}

class StatsCollectorStage extends GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]]{
  private val inboundRequest = Inlet[HttpRequest]("rq-in")
  private val outboundRequest = Outlet[HttpRequest]("rq-out")
  private val inboundResponse = Inlet[HttpResponse]("rp-in")
  private val outboundResponse = Outlet[HttpResponse]("rp-out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    import StatsCollectorStage._

    var inFlightData = Vector.empty[(Summary.Timer, HttpRequest)]
    val requestForward = new InHandler with OutHandler {
      override def onPush(): Unit = {
        val request = grab(inboundRequest)
        requestsInFlight.inc()
        inFlightData = inFlightData :+ (requestTimes.startTimer(), request)

        push(outboundRequest, request)
      }

      override def onPull(): Unit = pull(inboundRequest)
    }

    val responseForward = new InHandler with OutHandler {
      override def onPush(): Unit = {
        val response = grab(inboundResponse)
        val (start, request) = inFlightData.head
        val method = request.method.value
        val status = response.status.value
        inFlightData = inFlightData.tail

        requestsInFlight.dec()
        estimateSize(request).foreach(requestSize.labels(method, status).observe)
        estimateSize(response).foreach(responseSize.labels(method, status).observe)
        responsesTotal.labels(method, status).inc()
        start.close()

        push(outboundResponse, response)
      }

      override def onPull(): Unit = pull(inboundResponse)

      private def estimateSize(msg: HttpMessage): Option[Double] = {
        val entity = msg.entity()
        val contentLengthOption =
          if (entity.isKnownEmpty()) Some(0L) else entity.contentLengthOption
        contentLengthOption map { entitySize =>
          msg.headers.foldLeft(0.0) { (acc, next) =>
            acc + next.name().length + next.value().length + 4 // :, ' ', cr and nl
          } + entitySize
        }
      }
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
  implicit private class Quantiles(val b: Summary.Builder) extends AnyVal {
    def quantiles(values: Double*): b.type = {
      values.foreach(value => b.quantile(value, 1 - value))
      b
    }
  }

  private val requestsInFlight = Gauge.
    build("http_requests_in_flight", "Requests currently in flight").
    register()
  private val responsesTotal = Counter.
    build("http_requests_total", "Responses issued by the application").
    labelNames("method", "status").
    register()
  private val requestTimes = Summary.
    build("http_request_duration_microseconds", "Time to response determined").
    quantiles(0.01, 0.05, 0.5, 0.9, 0.95, 0.99).
    register()
  private val responseSize = Summary.
    build("http_response_size_bytes", "Response size (estimated)").
    quantiles(0.01, 0.05, 0.5, 0.9, 0.95, 0.99).
    labelNames("method", "code").
    register()
  private val requestSize = Summary.
    build("http_request_size_bytes", "Request size (estimated)").
    quantiles(0.01, 0.05, 0.5, 0.9, 0.95, 0.99).
    labelNames("method", "code").
    register()
}