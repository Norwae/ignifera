package com.github.norwae.ignifera

import akka.http.scaladsl.model._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}

import scala.collection.immutable.Queue
import scala.concurrent.duration._

/**
  * Stage intended to be joined to an akka http handler flow. The types are unchanged,
  * but on completion of a http request, metrics will be published to the observers.
  *
  */
class StatsCollectorStage(observers: Seq[HttpEventListener]) extends GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {
  private val inboundRequest = Inlet[HttpRequest]("rq-in")
  private val outboundRequest = Outlet[HttpRequest]("rq-out")
  private val inboundResponse = Inlet[HttpResponse]("rp-in")
  private val outboundResponse = Outlet[HttpResponse]("rp-out")

  final case class RequestData(method: HttpMethod, uri: Uri, requestSize: Option[Long], start: Long)

  private def estimateSize(msg: HttpMessage): Option[Long] = {
    val entity = msg.entity()
    val contentLengthOption =
      if (entity.isKnownEmpty()) Some(0L) else entity.contentLengthOption
    contentLengthOption map { entitySize =>
      msg.headers.foldLeft(entitySize) { (acc, next) =>
        acc + next.name().length + next.value().length + 4 // :, ' ', cr and nl
      }
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var inFlightData = Queue.empty[RequestData]

    private val requestForward: InHandler with OutHandler = new InHandler with OutHandler {
      override def onPush(): Unit = {
        val request = grab(inboundRequest)
        observers.foreach(_.onRequestStart())

        inFlightData = inFlightData :+ RequestData(request.method, request.uri, estimateSize(request), System.nanoTime())

        push(outboundRequest, request)
      }

      override def onPull(): Unit = pull(inboundRequest)

      override def onUpstreamFinish(): Unit = complete(outboundRequest)
    }

    private val responseForward: InHandler with OutHandler = new InHandler with OutHandler {
      override def onPush(): Unit = {
        val response = grab(inboundResponse)
        val respSize = estimateSize(response)
        val RequestData(method, uri, rqSize, start) = inFlightData.head
        val time = (System.nanoTime() - start).nanos
        inFlightData = inFlightData.tail

        observers.foreach(_.onRequestEnd(method, uri, rqSize, response.status, respSize, time))
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