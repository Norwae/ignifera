package com.github.norwae.ignifera

import akka.http.scaladsl.model.HttpEntity
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.{CollectorRegistry, Gauge}
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

class HttpExportSpec extends FlatSpec with Matchers {
  "The http export object" should "include metrics provided by the collector" in {
    val export =  new HttpExport {
      override val registry: CollectorRegistry = new CollectorRegistry()
    }
    val gauge = Gauge.build("testgauge", "Test").register(export.registry)
    gauge.inc()
    val reply = export.exportReply

    val entity = reply.entity.asInstanceOf[HttpEntity.Strict]
    val string = entity.data.utf8String
    string should containSubstring("testgauge")
  }

  it should "have the correct content type" in {
    val reply = new HttpExport {}.exportReply
    reply.entity.contentType.value.toLowerCase shouldEqual TextFormat.CONTENT_TYPE_004.toLowerCase
  }
  it should "include the default metrics" in {
    val reply = new HttpExport {}.exportReply
    val entity = reply.entity.asInstanceOf[HttpEntity.Strict]
    val string = entity.data.utf8String

    string should containSubstring("jvm_classes_loaded")
  }

  private def containSubstring(expected: String) =
    Matcher[String](left => MatchResult(left.contains(expected), s"Contain $expected", s"not contain $expected"))
}
