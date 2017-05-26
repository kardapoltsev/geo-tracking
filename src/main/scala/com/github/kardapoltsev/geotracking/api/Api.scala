package com.github.kardapoltsev.geotracking.api


import java.io.{ByteArrayOutputStream, IOException}
import java.util.zip.GZIPOutputStream

import android.content.Context
import android.util.Log
import okhttp3._
import spray.json._

import scala.concurrent.{Future, Promise}



object Api {
  private lazy val client = new OkHttpClient.Builder().authenticator(
    new Authenticator {
      override def authenticate(route: Route, response: Response): Request = {
        response.request().newBuilder().header(
          "Authorization", credentials
        ).build()
      }
    }
  ).build()

  import java.io.IOException

  private val Login = ""
  private val Password = ""
  private val ApiUrl = "http://"
  private val SendLocationUrl = ApiUrl + "geo/location"
  val jsonMediaType = MediaType.parse("application/json; charset=utf-8")
  val credentials = Credentials.basic(Login, Password)

  def sendLocation(locations: Seq[Location])(implicit ctx: Context): Future[String] = {
    //Log.d("API", s"sending ${locations.toJson.prettyPrint}")
    val body = RequestBody.create(jsonMediaType, gzip(locations))
    val request = new Request.Builder().
      url(SendLocationUrl).
      header("Content-Encoding", "gzip").
      post(body).
      build()

    val p = Promise[String]
    client.newCall(request).enqueue(new Callback(){
      override def onFailure(call: Call, e: IOException): Unit = {
        Log.e("API", "couldn't send location", e)
        p.failure(e)
      }

      override def onResponse(call: Call, response: Response): Unit = {
        Log.i("API", s"locations were sent: $response")
        //string() should close the body
        if(response.isSuccessful) {
          p.success(response.body().string())
        } else {
          p.failure(new Exception("couldn't save locations: " + response))
        }
      }
    })
    p.future
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
