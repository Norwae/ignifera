package com.github.norwae.ignifera

import akka.Done
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.concurrent.Future

class DefaultHealthFlow(readiness: () ⇒ Future[Done], onShutdown: () ⇒ Unit) extends GraphStage[FlowShape[HealthCheckType, HttpResponse]]{
  private val in = Inlet[HealthCheckType]("in")
  private val out = Outlet[HttpResponse]("out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
    private var pending = false
    private var shutdown = false

    private implicit def ec = materializer.executionContext

    private def provideResponse(code: StatusCode) = {
      pending = false
      maybeShutdown()
    }

    private def maybeShutdown() = {
      if (!pending && isClosed(in)) completeStage()
    }

    private val asyncProvideResponse = getAsyncCallback(provideResponse)

    override def onPush(): Unit = {
      pending = true
      grab(in) match {
        case HealthCheckType.Health ⇒ provideResponse(StatusCodes.NoContent)
        case HealthCheckType.RequestShutdown if !shutdown ⇒
          shutdown = true
          provideResponse(StatusCodes.NoContent)
          Future(onShutdown())
        case HealthCheckType.Readiness if !shutdown ⇒
          readiness() andThen PartialFunction(r ⇒ asyncProvideResponse.invoke(if (r.isSuccess) StatusCodes.NoContent else StatusCodes.InternalServerError))
        case _ ⇒ provideResponse(StatusCodes.InternalServerError)
      }
    }

    override def onPull(): Unit = pull(in)

    override def onUpstreamFinish(): Unit = maybeShutdown()

    setHandler(in, this)
    setHandler(out, this)
  }

  override def shape: FlowShape[HealthCheckType, HttpResponse] = FlowShape(in, out)
}
