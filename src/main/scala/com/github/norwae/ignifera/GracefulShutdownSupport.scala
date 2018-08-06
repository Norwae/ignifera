package com.github.norwae.ignifera

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.{Done, stream}
import com.github.norwae.ignifera.GracefulShutdownSupport.GSSShape

import scala.collection.immutable
import scala.concurrent.Future

object GracefulShutdownSupport {
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


  def noReadyCheck(): Future[Done] = Future.successful(Done)
  def noShutdownHandler(): Unit = ()
  def apply[A](flow: Flow[HttpRequest, HttpResponse, A],
               onReadyHandler: () ⇒ Future[Done] = noReadyCheck _,
               onShutdownHandler: () ⇒ Unit = noShutdownHandler _
              ): Flow[HttpRequest, HttpResponse, A] = {
    GracefulShutdownSupport(Flow.fromGraph(new DefaultHealthFlow(onReadyHandler, onShutdownHandler)), flow)
  }

  def apply[A](healthFlow: Flow[HealthCheckType, HttpResponse, Any], loadFlow: Flow[HttpRequest, HttpResponse, A]): Flow[HttpRequest, HttpResponse, A] = {
    Flow.fromGraph(GraphDSL.create(loadFlow) { implicit b ⇒ app ⇒
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
    private val responseHandler = new InHandler with OutHandler {
      override def onPush(): Unit = {
        push(responseOut, grab(if (isAvailable(appIn)) appIn else healthIn))
      }

      override def onPull(): Unit = {
        if (!isClosed(appIn) && !hasBeenPulled(appIn)) pull(appIn)
        if (!isClosed(healthIn) && !hasBeenPulled(healthIn)) pull(healthIn)
      }

      override def onUpstreamFinish(): Unit = if (isClosed(appIn) && isClosed(healthIn)) completeStage()
    }

    private val requestHandler = new InHandler with OutHandler {
      import akka.http.scaladsl.model.HttpMethods._
      import HealthCheckType._

      override def onPush(): Unit = grab(requestIn) match {
        case HttpRequest(GET, HealthUri(), _, _, _) ⇒ push(healthOut, Health)
        case HttpRequest(GET, ReadinessUri(), _ , _, _) ⇒ push(healthOut, Readiness)
        case HttpRequest(DELETE, ReadinessUri(), _, _, _) ⇒ push(healthOut, RequestShutdown)
        case other ⇒ push(appOut, other)
      }


      override def onUpstreamFinish(): Unit = {
        complete(healthOut)
        complete(appOut)
      }

      override def onPull(): Unit = if (isAvailable(healthOut) && isAvailable(appOut)) pull(requestIn)
    }

    setHandler(responseOut, responseHandler)
    setHandler(appIn, responseHandler)
    setHandler(healthIn, responseHandler)

    setHandler(requestIn, requestHandler)
    setHandler(appOut, requestHandler)
    setHandler(healthOut, requestHandler)
  }
}
