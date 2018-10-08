package com.github.norwae.ignifera

import akka.Done
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.PushGateway

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
  * Configuration class for [[DefaultHealthFlow]]
  *
  * @param readiness            readiness callback
  * @param onShutdown           shutdown callback
  * @param shutdownPushEndpoint endpoint for final prometheus metrics push before shutdown
  */
case class HealthFlowConfiguration(
  readiness: () => Future[Done],
  onShutdown: () => Unit,
  shutdownPushEndpoint: Option[String]
)

/**
  * Default implementation for health / graceful shutdown handling. The
  * `/health` route always returns `204`, the `/health/readiness` route
  * invokes a callback, and returns depending on success or failure.
  *
  * Receiving a [[HealthCheckType.RequestShutdown]] causes the
  * `/health/readiness` to always return 500.
  *
  * @param config [[HealthFlowConfiguration]] containing config parameter
  */
class DefaultHealthFlow(config: HealthFlowConfiguration) extends GraphStage[FlowShape[HealthCheckType, HttpResponse]] {
  private val in = Inlet[HealthCheckType]("in")
  private val out = Outlet[HttpResponse]("out")

  var pushGateway: Option[PushGateway] = if(config.shutdownPushEndpoint isDefined) Some(new PushGateway(config.shutdownPushEndpoint get)) else None

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
      import config._

      pending = true
      val checkType = grab(in)
      checkType match {
        case HealthCheckType.Health ⇒ provideResponse(goodStatus)
        case HealthCheckType.RequestShutdown if !shutdown ⇒
          shutdown = true
          provideResponse(goodStatus)
          Future(onShutdown())
          if (pushGateway isDefined) {
            pushGateway.get.push(CollectorRegistry.defaultRegistry, "")
          }
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
