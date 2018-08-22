package com.github.norwae.ignifera

import akka.http.scaladsl.model.HttpMethods.{DELETE, GET}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.{Done, stream}
import com.github.norwae.ignifera.GracefulShutdownSupport.GSSShape
import com.github.norwae.ignifera.HealthCheckType.{Health, Readiness, RequestShutdown}

import scala.collection.immutable
import scala.concurrent.Future

object GracefulShutdownSupport {

  /**
    * The rather peculiar shape of the graceful shutdown stage. It consists of
    * three inlets and outlets.
    *
    * {{{
    *             +------+
    *   mainIn ~> |      | ~> healthOut
    *             |      | <~ healthIn
    *             | GSS  |
    *             |      | ~> appOut
    *  mainOut <~ |      | <~ appIn
    *             +------+
    * }}}
    *
    * Conceptually, the healthOut and healthIn should be connected to a flow
    * handling the health requests, and the appOut and appIn connected
    * to a flow handling the main application logic.
    *
    * @param mainIn    request input
    * @param mainOut   response output
    * @param appIn     application responses
    * @param appOut    application requests
    * @param healthIn  health responses
    * @param healthOut health requests
    */
  class GSSShape(val mainIn: Inlet[HttpRequest], val mainOut: Outlet[HttpResponse],
                 val appIn: Inlet[HttpResponse], val appOut: Outlet[HttpRequest],
                 val healthIn: Inlet[HttpResponse], val healthOut: Outlet[HealthCheckType]) extends stream.Shape {

    override def inlets: immutable.Seq[Inlet[_]] = List(mainIn, appIn, healthIn)

    override def outlets: immutable.Seq[Outlet[_]] = List(mainOut, appOut, healthOut)

    override def deepCopy(): stream.Shape = new GSSShape(
      mainIn.carbonCopy(), mainOut.carbonCopy(),
      appIn.carbonCopy(), appOut.carbonCopy(),
      healthIn.carbonCopy(), healthOut.carbonCopy()
    )
  }


  /** do not perform a deep readiness check */
  def noReadyCheck(): Future[Done] = Future.successful(Done)

  /** do no explicit shutdown handling, just wait for requests to drain */
  def noShutdownHandler(): Unit = ()

  /** construct a new graceful shutdown stage. The health flow will be provided
    * by a [[DefaultHealthFlow]]. The [[GSSShape.mainIn]] and [[GSSShape.mainOut]]
    * will remain unconnected and provide the open ports of the stage.
    *
    * @param flow              main application flow
    * @param onReadyHandler    readiness callback. Defaults to [[noReadyCheck()]]
    * @param onShutdownHandler shutdown handler. Defaults to [[noShutdownHandler()]]
    * @tparam A materialized value of the inner flow
    * @return adapted flow
    */
  def apply[A](flow: Flow[HttpRequest, HttpResponse, A],
               onReadyHandler: () ⇒ Future[Done] = noReadyCheck _,
               onShutdownHandler: () ⇒ Unit = noShutdownHandler _
              ): Flow[HttpRequest, HttpResponse, A] = {
    GracefulShutdownSupport(Flow.fromGraph(new DefaultHealthFlow(onReadyHandler, onShutdownHandler)), flow)
  }

  /** construct a new graceful shutdown stage. The [[GSSShape.mainIn]] and [[GSSShape.mainOut]]
    * will remain unconnected and provide the open ports of the stage.
    *
    * @param healthFlow      flow to provide health check
    * @param applicationFlow flow providing the main application logic
    * @tparam A materialized value type
    * @return adapted flow
    */
  def apply[A](healthFlow: Flow[HealthCheckType, HttpResponse, Any], applicationFlow: Flow[HttpRequest, HttpResponse, A]): Flow[HttpRequest, HttpResponse, A] = {
    Flow.fromGraph(GraphDSL.create(applicationFlow) { implicit b ⇒
      app ⇒
        val coordinator = b add new GracefulShutdownSupport
        val health = b add healthFlow
        import GraphDSL.Implicits._

        coordinator.healthOut ~> health
        coordinator.healthIn <~ health

        coordinator.appOut ~> app
        coordinator.appIn <~ app

        FlowShape(coordinator.mainIn, coordinator.mainOut)
    })
  }
}

/**
  * Provides graceful shutdown support by forking off the `/health` and `/health/readiness`
  * routes towards a dedicated handler flow, and passing all other traffic to the main
  * flow.
  */
class GracefulShutdownSupport extends GraphStage[GSSShape] {

  import GracefulShutdownSupport.GSSShape

  private val requestIn = Inlet[HttpRequest]("requestIn")
  private val responseOut = Outlet[HttpResponse]("responseOut")

  private val healthOut = Outlet[HealthCheckType]("healthOut")
  private val appOut = Outlet[HttpRequest]("appOut")
  private val healthIn = Inlet[HttpResponse]("healthIn")
  private val appIn = Inlet[HttpResponse]("appIn")


  private object HealthUri {
    def unapply(uri: Uri): Boolean = uri.path == Path / "health"
  }

  private object ReadinessUri {
    def unapply(uri: Uri): Boolean = uri.path == Path / "health" / "readiness"
  }

  override def shape = new GSSShape(
    requestIn, responseOut,
    appIn, appOut,
    healthIn, healthOut
  )

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var requestPending = false

    private def pullRequestIfReady(): Unit = {
      if (!requestPending && isAvailable(appOut) && isAvailable(healthOut) && !hasBeenPulled(requestIn)) {
        pull(requestIn)
      }
    }

    private val requestInputHandler: InHandler = new InHandler {
      override def onPush(): Unit = {
        requestPending = true
        grab(requestIn) match {
          case HttpRequest(GET, HealthUri(), _, _, _) ⇒ push(healthOut, Health)
          case HttpRequest(GET, ReadinessUri(), _, _, _) ⇒ push(healthOut, Readiness)
          case HttpRequest(DELETE, ReadinessUri(), _, _, _) ⇒ push(healthOut, RequestShutdown)
          case other ⇒ push(appOut, other)
        }
      }

      override def onUpstreamFinish(): Unit = {
        complete(healthOut)
        complete(appOut)
      }
    }

    private def outputForwardHandler(in: Inlet[HttpResponse]) = new InHandler {
      override def onPush(): Unit = {
        push(responseOut, grab(in))
        requestPending = false
      }

      override def onUpstreamFinish(): Unit = {
        if (isClosed(appIn) && isClosed(healthIn)) completeStage()
      }
    }

    val responseOutputHandler: OutHandler = () ⇒ pullRequestIfReady()

    setHandler(requestIn, requestInputHandler)
    setHandler(appOut, responseOutputHandler)
    setHandler(healthOut, responseOutputHandler)
    setHandler(appIn, outputForwardHandler(appIn))
    setHandler(healthIn, outputForwardHandler(healthIn))

    setHandler(responseOut, () ⇒ {
      if (!hasBeenPulled(healthIn)) tryPull(healthIn)
      if (!hasBeenPulled(appIn)) tryPull(appIn)
      pullRequestIfReady()
    })
  }
}
