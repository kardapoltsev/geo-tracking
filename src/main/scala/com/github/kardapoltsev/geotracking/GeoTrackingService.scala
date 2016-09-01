package com.github.kardapoltsev.geotracking

import java.util.logging.Logger

import android.content.Intent
import android.os.IBinder
import org.scaloid.common.SService

class GeoTrackingService extends SService {
  private val log = Logger.getLogger(getClass.getSimpleName)

  override def onBind(intent: Intent): IBinder = {
    //don't provide binding
    null
  }

  onCreate {
    log.info("starting geo tracking service")
  }

}
