package com.github.kardapoltsev.geotracking

import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import org.scaloid.common._
import android.content.pm.PackageManager



class GeoTrackingActivity extends SActivity {
  private val PermissionRequestCode = 31528

  onCreate {
    drawMainScreen()

    val requiredPermission = android.Manifest.permission.ACCESS_FINE_LOCATION
    val permissionCheckResult = ContextCompat.checkSelfPermission(
      this, requiredPermission
    )
    info(s"permissionCheckResult = $permissionCheckResult")

    if(permissionCheckResult != PackageManager.PERMISSION_GRANTED) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(this, requiredPermission)) {
        //show explanation
        info("should show explanation")
      } else {
        info("requesting permissions")
        ActivityCompat.requestPermissions(
          this,
          Array[String](requiredPermission),
          PermissionRequestCode
        )
      }
    } else {
      startTracking()
    }
  }

  private def drawMainScreen(): Unit = {
    val l = new SVerticalLayout {
      STextView("Geo tracking")
      SButton("start tracking", startTracking())
      SButton("stop tracking", stopTracking())
    }
    setContentView(l)
  }

  private def startTracking(): Unit = {
    startForegroundService(SIntent[GeoTrackingService])
//    startService[GeoTrackingService]
  }

  private def stopTracking(): Unit = {
    stopService[GeoTrackingService]
  }

  override def onRequestPermissionsResult(
    requestCode: Int, permissions: Array[String], grantResults: Array[Int]
  ) {
    requestCode match {
      case PermissionRequestCode =>
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && grantResults(0) == PackageManager.PERMISSION_GRANTED) {
          info("permissions granted, starting geo tracking service")
          startTracking()
        } else {
          warn("permission denied, stopping...")
          finish()
        }
    }
  }

}
