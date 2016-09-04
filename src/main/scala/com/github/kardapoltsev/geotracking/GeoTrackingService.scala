package com.github.kardapoltsev.geotracking


import android.app.Service
import android.content.{Context, Intent}
import android.hardware._
import android.location.{Criteria, Location, LocationListener, LocationManager}
import android.os.{Bundle, Handler, IBinder}
import com.github.kardapoltsev.geotracking.api.Api
import org.scaloid.common.{Logger, SService}

import scala.collection.mutable



class GeoTrackingService extends SService with Logger
  with LocationListener {
  val MinInterval = 0L
  val MinDistance = 10F
  val MinLocationsToSend = 20

  private val locations = mutable.ListBuffer[api.Location]()

  private var locationManager: LocationManager = _
  private var sensorManager: SensorManager = _

  private var handler: Handler = _
  private var lastLocationUpdateTime = -1L
  private val CheckMotionInterval = 60 * 2 * 1000
  private val NoMotionInterval = 60 * 1000


  override def onBind(intent: Intent): IBinder = {
    //don't provide binding
    null
  }

  onCreate {
    info(s"starting geo tracking service. MinDistance = $MinDistance m, MinLocationsToSend = $MinLocationsToSend")
    handler = new Handler
    locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
    sensorManager = getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager]
    registerForGpsUpdates()
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
    Api.sendLocation(locations.toList)
    locations.clear()
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
