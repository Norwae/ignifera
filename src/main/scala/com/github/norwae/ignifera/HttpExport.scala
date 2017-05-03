package com.github.norwae.ignifera

import java.io.StringWriter

import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse}
import akka.util.ByteString
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports

trait HttpExport {
  DefaultExports.initialize()

  val registry: CollectorRegistry = CollectorRegistry.defaultRegistry

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
