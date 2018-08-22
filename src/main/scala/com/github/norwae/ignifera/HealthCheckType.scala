package com.github.norwae.ignifera

/**
  * Three kinds of health check operations are supported for this library:
  *
  * * Health check
  * * Readiness check
  * * Shutdown request
  *
  * Usually, kubernetes requests shutdown by sending `SIGTERM` to the process. Since
  * this involved heavy JVM interaction, and will trigger an immediate shutdown, we
  * require a wrapper to map this signal to the shutdown request.
  */
sealed trait HealthCheckType

object HealthCheckType {

  /** GET /health */
  case object Health extends HealthCheckType

  /** GET /health/readiness */
  case object Readiness extends HealthCheckType

  /** DELETE /health/readiness */
  case object RequestShutdown extends HealthCheckType

}
