package com.tsingb.tim.http.util

import scala.collection.mutable.Set

import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.ContentTypes

object HttpResponseUtil {

  private val gzip = Gzip
  def entityResponse(json: String): HttpResponse = {
    val header = RawHeader("Access-Control-Allow-Origin", "*")
    val headers = Set.empty[HttpHeader]
    headers += header
    gzip.encode(HttpResponse(status = 200, entity = HttpEntity(ContentTypes.`application/json`, json), headers = headers.toList))
  }

  def entityResponse(json: String, headers: List[HttpHeader]): HttpResponse = {
    val header = RawHeader("Access-Control-Allow-Origin", "*")
    val headerset = Set.empty[HttpHeader]
    headerset += header
    headers.foreach { h => headerset += h }
    gzip.encode(HttpResponse(status = 200, entity = HttpEntity(ContentTypes.`application/json`, json), headers = headers.toList))
  }

  def entityResponse(json: String, token: String): HttpResponse = {
    val header = RawHeader("Authorization", token)
    val headers = Set.empty[HttpHeader]
    headers += header
    gzip.encode(HttpResponse(status = 200, entity = HttpEntity(ContentTypes.`application/json`, json), headers = headers.toList))
  }

  def entityResponse(code: Int, json: String): HttpResponse = {
    val header = RawHeader("Access-Control-Allow-Origin", "*")
    val headers = Set.empty[HttpHeader]
    headers += header
    gzip.encode(HttpResponse(status = code, entity = HttpEntity(ContentTypes.`application/json`, json), headers = headers.toList))
  }

}