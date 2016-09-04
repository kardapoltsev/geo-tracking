package com.github.kardapoltsev.geotracking.api


import java.io.{ByteArrayOutputStream, IOException}
import java.util.zip.GZIPOutputStream

import android.content.Context
import android.util.Log
import okhttp3._
import spray.json._



object Api {
  private lazy val client = new OkHttpClient()
  private val ApiUrl = ""
  private val SendLocationUrl = ApiUrl + "geo/location"
  val jsonMediaType = MediaType.parse("application/json; charset=utf-8")

  def sendLocation(locations: Seq[Location])(implicit ctx: Context): Unit = {
    if(locations.nonEmpty) {
      //Log.d("API", s"sending ${locations.toJson.prettyPrint}")
      val body = RequestBody.create(jsonMediaType, gzip(locations))
      val request = new Request.Builder().
        url(SendLocationUrl).
        header("Content-Encoding", "gzip").
        post(body).
        build()

      client.newCall(request).enqueue(new Callback(){
        override def onFailure(call: Call, e: IOException): Unit = {
          Log.e("API", "couldn't send location", e)
        }

        override def onResponse(call: Call, response: Response): Unit = {
          Log.i("API", s"locations were sent: $response")
        }
      })
    }
  }

  private def gzip(locations: Seq[Location]): Array[Byte] = {
    val data = locations.toJson.compactPrint.getBytes("UTF-8")
    val arr = new ByteArrayOutputStream()
    val zipper = new GZIPOutputStream(arr)
    zipper.write(data)
    zipper.close()
    arr.toByteArray
  }

}
