package com.github.kardapoltsev.geotracking


import android.content.{ContentValues, Context, Intent}
import android.hardware._
import android.location.{Criteria, Location, LocationListener, LocationManager}
import android.net.ConnectivityManager
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
  private val MinInterval = 0L
  private val MinDistance = 0F
  private val MinLocationsToSend = 100
  //setting this to 1000 results in
  //android.database.sqlite.SQLiteException: too many SQL variables (code 1): , while compiling: DELETE FROM locations WHERE _id in (?,?,?,?,?,? ... )
  private val MaxLocationsToSend = 500
  private val IsSignificantMotionDetectionAllowed = false
  private val WifiOnly = false

  implicit val ec = ExecutionContext.fromExecutor(
    AsyncTask.THREAD_POOL_EXECUTOR
  )

  private lazy val dbHelper = new LocationDbHelper()
  private lazy val database = dbHelper.getWritableDatabase

  private val locations = mutable.ListBuffer[api.Location]()

  private var locationManager: LocationManager = _
  private var sensorManager: SensorManager = _
  private var connectivityManager: ConnectivityManager = _

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
    connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
    registerForGpsUpdates()
    sendUnsentLocations()
  }

  onDestroy {
    info(s"stopping geo tracking service")
    stopMotionChecker()
    unregisterFromGpsUpdates()
    dbHelper.close()
  }

  //
  // Motion detection
  //

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
      && System.currentTimeMillis() - lastLocationUpdateTime > NoMotionInterval
      && IsSignificantMotionDetectionAllowed
    ) {
      info("device is stopped")
      unregisterFromGpsUpdates()
      registerForMotionDetection()
    } else if(IsSignificantMotionDetectionAllowed) {
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
    flushLocationsBatch() //ensure that last locations were sent
  }

  //
  // Send locations stuff
  //

  private def flushLocationsBatch(): Unit = {
    val batch = locations.toList
    locations.clear()
    storeLocations(batch)
    sendUnsentLocations()
  }

  private def storeLocations(locations: Seq[api.Location]): Unit = {
    database.beginTransaction()
    try {
      locations.foreach { unsent =>
        val value = new ContentValues(1)
        value.put(LocationEntry.LocationColumnName, unsent.toJson.compactPrint)

        database.insertOrThrow(
          LocationEntry.TableName,
          null,
          value
        )
      }
      info(s"saved ${locations.size} locations")
      database.setTransactionSuccessful()
    } catch {
      case e: Exception =>
        error("couldn't insert locations", e)
    } finally {
      database.endTransaction()
    }

  }

  private def sendUnsentLocations(): Unit = {
    if(!WifiOnly || isWifi) {
      val projection = Array(LocationEntry._ID, LocationEntry.LocationColumnName)
      val unsent = database.query(
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
            if(database.isOpen) { //service could be already stopped
              val inClause = unsent.map(_ => "?").
                mkString(LocationEntry._ID + " in (", ",", ")")

              info(s"${unsent.size} unsent locations were sent")
              database.delete(
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
    }
  }

  private def isWifi: Boolean = {
    val info = connectivityManager.getActiveNetworkInfo
    Option(info).fold(false) { i =>
      i.isConnected && i.getType == ConnectivityManager.TYPE_WIFI
    }
  }

  //
  // Location listener interface
  //

  override def onProviderEnabled(provider: String): Unit = { }
  override def onStatusChanged(provider: String, status: Int, extras: Bundle): Unit = { }
  override def onProviderDisabled(provider: String): Unit = { }

  override def onLocationChanged(location: Location): Unit = {
    debug(s"got new location with accuracy: ${location.getAccuracy} ")
    locations += api.Location(
      latitude = location.getLatitude,
      longitude = location.getLongitude,
      altitude = Some(location.getAltitude),
      timestamp = location.getTime,
      horizontalAccuracy = location.getAccuracy,
      verticalAccuracy = None,
      speed = location.getSpeed
    )
    if(locations.lengthCompare(MinLocationsToSend) == 0) {
      flushLocationsBatch()
    }
    lastLocationUpdateTime = System.currentTimeMillis()
  }

}
