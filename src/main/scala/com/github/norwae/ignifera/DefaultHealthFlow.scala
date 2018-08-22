package com.github.norwae.ignifera

import akka.Done
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
  * Default implementation for health / graceful shutdown handling. The
  * `/health` route always returns `204`, the `/health/readiness` route
  * invokes a callback, and returns depending on success or failure.
  *
  * Receiving a [[HealthCheckType.RequestShutdown]] causes the
  * `/health/readiness` to always return 500.
  *
  * @param readiness  readiness callback
  * @param onShutdown shutdown callback
  */
class DefaultHealthFlow(readiness: () ⇒ Future[Done], onShutdown: () ⇒ Unit) extends GraphStage[FlowShape[HealthCheckType, HttpResponse]] {
  private val in = Inlet[HealthCheckType]("in")
  private val out = Outlet[HttpResponse]("out")

  def badStatus: StatusCode = StatusCodes.InternalServerError

  def goodStatus: StatusCode = StatusCodes.NoContent

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
    private var pending = false
    private var shutdown = false

    private implicit def ec: ExecutionContext = materializer.executionContext

    private def provideResponse(code: StatusCode): Unit = {
      push(out, HttpResponse(code))
      pending = false
      maybeShutdown()
    }

    private def maybeShutdown(): Unit = {
      if (!pending && isClosed(in)) completeStage()
    }

    private val asyncProvideResponse = getAsyncCallback(provideResponse)

    override def onPush(): Unit = {
      pending = true
      val checkType = grab(in)
      checkType match {
        case HealthCheckType.Health ⇒ provideResponse(goodStatus)
        case HealthCheckType.RequestShutdown if !shutdown ⇒
          shutdown = true
          provideResponse(goodStatus)
          Future(onShutdown())
        case HealthCheckType.Readiness if !shutdown ⇒
          readiness().
            transform(t ⇒ Success(if (t.isSuccess) goodStatus else badStatus)).
            foreach(asyncProvideResponse.invoke)
        case _ ⇒ provideResponse(badStatus)
      }
    }

    override def onPull(): Unit = pull(in)

    override def onUpstreamFinish(): Unit = maybeShutdown()

    setHandler(in, this)
    setHandler(out, this)
  }

  override def shape: FlowShape[HealthCheckType, HttpResponse] = FlowShape(in, out)
}
