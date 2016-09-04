package com.github.kardapoltsev.geotracking.api

import spray.json.DefaultJsonProtocol

case class Location(
  latitude: Double,
  longitude: Double,
  altitude: Option[Double],
  timestamp: Long,
  horizontalAccuracy: Double,
  verticalAccuracy: Option[Double],
  speed: Double
)
object Location extends DefaultJsonProtocol {
  implicit val _ = jsonFormat(
    Location.apply, "latitude", "longitude", "altitude", "timestamp", "horizontalAccuracy", "verticalAccuracy", "speed"
  )
}
