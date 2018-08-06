package com.github.norwae.ignifera

sealed trait HealthCheckType

object HealthCheckType {
  case object Health extends HealthCheckType
  case object Readiness extends HealthCheckType
  case object RequestShutdown extends HealthCheckType
}
