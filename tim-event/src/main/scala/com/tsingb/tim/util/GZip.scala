package com.tsingb.tim.util

import java.util.zip.GZIPInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayInputStream

object GZip {

  def unGZip(data: Array[Byte]): Array[Byte] = {
    try {
      val bain = new ByteArrayInputStream(data)
      val gzip = new GZIPInputStream(bain)
      val baos = new ByteArrayOutputStream()
      val buf = Stream.continually(gzip.read()).takeWhile(_ != -1).map(_.toByte).toArray
      baos.write(buf)
      val bs = baos.toByteArray()
      baos.flush()
      baos.close()
      gzip.close()
      bs
    } catch {
      case e: Throwable =>
        throw new RuntimeException(e)
    }
  }

  def gzip(data: Array[Byte]): Array[Byte] = {
    try {

      val baos = new ByteArrayOutputStream();
      val gos = new GZIPOutputStream(baos)
      gos.write(data)
      gos.finish()
      gos.flush()
      gos.close()
      val output = baos.toByteArray()
      baos.flush()
      baos.close()
      output
    } catch {
      case e: Throwable =>
        throw new RuntimeException(e)
    }
  }

}