package com.github.kardapoltsev.geotracking


import android.content.{ContentValues, Context, Intent}
import android.hardware._
import android.location.{Criteria, Location, LocationListener, LocationManager}
import android.os.{AsyncTask, Bundle, IBinder}
import com.github.kardapoltsev.geotracking.api.Api
import com.github.kardapoltsev.geotracking.db.LocationDbHelper
import com.github.kardapoltsev.geotracking.db.LocationEntry
import org.scaloid.common.{DatabaseImplicits, Logger, SService}
import spray.json._
import org.scaloid.common._

import scala.collection.mutable
import scala.concurrent.ExecutionContext



class GeoTrackingService extends SService with DatabaseImplicits with Logger
  with LocationListener {
  val MinInterval = 0L
  val MinDistance = 5F
  val MinLocationsToSend = 100
  val MaxLocationsToSend = 500

  implicit val ec = ExecutionContext.fromExecutor(
    AsyncTask.THREAD_POOL_EXECUTOR
  )

  private val dbHelper = new LocationDbHelper

  private val locations = mutable.ListBuffer[api.Location]()

  private var locationManager: LocationManager = _
  private var sensorManager: SensorManager = _

  private var lastLocationUpdateTime = -1L
  private val NoMotionInterval = 5 * 60 * 1000
  private val CheckMotionInterval = 60 * 1000


  override def onBind(intent: Intent): IBinder = {
    //don't provide binding
    null
  }

  onCreate {
    info(s"starting geo tracking service. MinDistance = $MinDistance m, MinLocationsToSend = $MinLocationsToSend")
    locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
    sensorManager = getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager]
    registerForGpsUpdates()
    sendUnsentLocations()
  }

  onDestroy {
    info(s"stopping geo tracking service")
    stopMotionChecker()
    unregisterFromGpsUpdates()
  }

  private def startMotionChecker(): Unit = {
    lastLocationUpdateTime = -1
    handler.postDelayed(motionChecker, CheckMotionInterval)
  }

  private def stopMotionChecker(): Unit = {
    handler.removeCallbacks(motionChecker)
  }

  private val motionChecker: Runnable = new Runnable {
    override def run(): Unit = {
      checkMotion()
    }
  }

  private def checkMotion(): Unit = {
    info(s"checking device motion using lastLocationUpdateTime")
    if(lastLocationUpdateTime > 0
      && System.currentTimeMillis() - lastLocationUpdateTime > NoMotionInterval) {
      info("device is stopped")
      unregisterFromGpsUpdates()
      registerForMotionDetection()
    } else {
      info("device is moving")
      handler.postDelayed(motionChecker, CheckMotionInterval)
    }
  }

  private def registerForMotionDetection(): Unit = {
    info("registering for motion detection")
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    val listener = new TriggerEventListener {
      override def onTrigger(event: TriggerEvent): Unit = {
        info(s"${event.sensor} reports about motion")
        info(s"sensor values: ${event.values.toList} ")
        registerForGpsUpdates()
      }
    }
    sensorManager.requestTriggerSensor(listener, sensor)
  }


  private def registerForGpsUpdates(): Unit = {
    info("registering for gps updates")
    val locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
    val c = new Criteria()
    c.setHorizontalAccuracy(Criteria.ACCURACY_FINE)
    val bestProvider = locationManager.getBestProvider(c, false)

    if(bestProvider != LocationManager.GPS_PROVIDER) {
      warn(s"bestProvider isn't gps: $bestProvider")
    }

    locationManager.requestLocationUpdates(
      bestProvider, MinInterval, MinDistance, this
    )
    startMotionChecker()
  }

  private def unregisterFromGpsUpdates(): Unit = {
    stopMotionChecker()
    locationManager.removeUpdates(this)
    sendLocations() //ensure that last locations were sent
  }

  private def sendLocations(): Unit = {
    info(s"sending ${locations.length} locations")
    val l = locations.toList
    Api.sendLocation(l) onFailure {
      case e =>
        val db = dbHelper.getWritableDatabase
        db.beginTransaction()
        try {
          l.foreach { unsent =>
            val value = new ContentValues(1)
            value.put(LocationEntry.LocationColumnName, unsent.toJson.compactPrint)

            dbHelper.getWritableDatabase.insertOrThrow(
              LocationEntry.TableName,
              null,
              value
            )
          }
          info(s"saved ${l.size} unsent locations")
          db.setTransactionSuccessful()
        } catch {
          case e: Exception =>
            error("couldn't insert unsent locations", e)
        } finally {
          db.endTransaction()
        }

    }
    locations.clear()
  }

  private def sendUnsentLocations(): Unit = {
    val projection = Array(LocationEntry._ID, LocationEntry.LocationColumnName)
    val unsent = dbHelper.getReadableDatabase.query(
      LocationEntry.TableName,
      projection,
      null, //selection
      null, //selection args
      null, //group
      null, //filter
      null,  //order
      MaxLocationsToSend.toString // limit
    ).closeAfter(_.map { c =>
      val id = c.getInt(c.getColumnIndexOrThrow(LocationEntry._ID))
      val l = c.getString(
        c.getColumnIndexOrThrow(LocationEntry.LocationColumnName)
      ).parseJson.convertTo[api.Location]

      LocationEntry(id, l)
    })

    if(unsent.nonEmpty) {
      Api.sendLocation(unsent.map(_.location).toSeq) onSuccess { case response =>
        runOnUiThread {
          val inClause = unsent.map(_ => "?").
            mkString(LocationEntry._ID + " in (", ",", ")")

          info(s"${unsent.size} unsent locations were sent")
          dbHelper.getWritableDatabase.delete(
            LocationEntry.TableName,
            inClause,
            unsent.map(_.id.toString).toArray
          )
          if (unsent.size == MaxLocationsToSend) {
            //we have more unsent in database
            sendUnsentLocations()
          }
        }
      }
    }
  }

  //
  // Location listener interface
  //

  override def onProviderEnabled(provider: String): Unit = { }
  override def onStatusChanged(provider: String, status: Int, extras: Bundle): Unit = { }
  override def onProviderDisabled(provider: String): Unit = { }

  override def onLocationChanged(location: Location): Unit = {
    info(s"got new location with accuracy: ${location.getAccuracy} ")
    locations += api.Location(
      latitude = location.getLatitude,
      longitude = location.getLongitude,
      altitude = Some(location.getAltitude),
      timestamp = System.currentTimeMillis(),
      horizontalAccuracy = location.getAccuracy,
      verticalAccuracy = None,
      speed = location.getSpeed
    )
    if(locations.length == MinLocationsToSend) {
      sendLocations()
    }
    lastLocationUpdateTime = System.currentTimeMillis()
  }


}
