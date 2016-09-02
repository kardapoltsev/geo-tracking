package com.github.kardapoltsev.geotracking


import android.content.{Context, Intent}
import android.location.{Criteria, Location, LocationListener, LocationManager}
import android.os.{Bundle, IBinder}
import com.github.kardapoltsev.geotracking.api.Api
import org.scaloid.common.{Logger, SService}

import scala.collection.mutable

class GeoTrackingService extends SService with Logger {
  private val locations = mutable.ListBuffer[api.Location]()
  val MinInterval = 0L
  val MinDistance = 10F
  val MinLocationsToSend = 100


  override def onBind(intent: Intent): IBinder = {
    //don't provide binding
    null
  }

  onCreate {
    info("starting geo tracking service")
    registerForUpdates()
  }


  private def registerForUpdates(): Unit = {
    val locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
    val c = new Criteria()
    c.setHorizontalAccuracy(Criteria.ACCURACY_FINE)
    val bestProvider = locationManager.getBestProvider(c, false)

    if(bestProvider != LocationManager.GPS_PROVIDER) {
      warn(s"bestProvider isn't gps: $bestProvider")
    }

    locationManager.requestLocationUpdates(
      bestProvider, MinInterval, MinDistance, newLocationListener
    )

  }


  private def newLocationListener: LocationListener = {
    new LocationListener {
      override def onProviderEnabled(provider: String): Unit = {

      }

      override def onStatusChanged(provider: String, status: Int, extras: Bundle): Unit = { }

      override def onLocationChanged(location: Location): Unit = {
        info(s"Thread ${Thread.currentThread().getId} got new location with accuracy: ${location.getAccuracy} ")
        locations += api.Location(
          lat = location.getLatitude,
          lon = location.getLongitude,
          alt = location.getAltitude,
          ts = System.currentTimeMillis(),
          hacc = location.getAccuracy,
          vacc = location.getAccuracy,
          speed = location.getSpeed
        )
        if(locations.length == MinLocationsToSend) {
          sendLocations()
        }
      }

      override def onProviderDisabled(provider: String): Unit = { }
    }
  }

  private def sendLocations(): Unit = {
    info(s"sending ${locations.length} locations")
    Api.sendLocation(locations.toList)
    locations.clear()
  }

}
