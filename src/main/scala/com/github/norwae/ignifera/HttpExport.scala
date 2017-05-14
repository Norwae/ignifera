package com.github.norwae.ignifera

import java.io.StringWriter

import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse}
import akka.util.ByteString
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports

/**
  * Provides basic export functions, providing a [[HttpResponse]] with the correct
  * contents and content type.
  *
  * Creating an instance of this trait will initialize the default exports.
  *
  * @see [[DefaultExports.initialize()]]
  */
trait HttpExport {
  DefaultExports.initialize()

  protected val registry: CollectorRegistry = CollectorRegistry.defaultRegistry

  /**
    * Queries the [[registry]] and produces an [[HttpResponse]] object that encapsulates
    * the entire response. The entire response will be loaded into memory.
    *
    * @return response
    */
  def exportReply: HttpResponse = {
    val writer = new StringWriter
    TextFormat.write004(writer, registry.metricFamilySamples())
    val string = writer.toString

    HttpResponse(entity = HttpEntity.Strict(HttpExport.contentType, ByteString(string)))
  }
}

object HttpExport {
  val contentType: ContentType = ContentType.parse(TextFormat.CONTENT_TYPE_004).right.get
}
