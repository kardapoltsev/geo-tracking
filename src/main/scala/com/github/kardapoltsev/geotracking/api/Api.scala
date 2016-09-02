package com.github.kardapoltsev.geotracking.api


import android.content.Context
import android.util.Log
import com.loopj.android.http.{AsyncHttpClient, AsyncHttpResponseHandler}
import cz.msebera.android.httpclient.Header
import cz.msebera.android.httpclient.entity.StringEntity
import spray.json._



object Api {
  private lazy val client = new AsyncHttpClient()

  def sendLocation(locations: Seq[Location])(implicit ctx: Context): Unit = {
    if(locations.nonEmpty) {
      Log.d("API", s"sending ${locations.toJson.prettyPrint}")
      val entity = new StringEntity("json here")
      //client.post(ctx, "", entity, "application/json", sendLocationResponseHandler)
    }
  }

  private def sendLocationResponseHandler =
    new AsyncHttpResponseHandler() {
      override def onFailure(
        statusCode: Int,
        headers: Array[Header],
        responseBody: Array[Byte],
        error: Throwable): Unit = {

      }

      override def onSuccess(
        statusCode: Int,
        headers: Array[Header],
        responseBody: Array[Byte]
      ): Unit = {

      }
    }


}
