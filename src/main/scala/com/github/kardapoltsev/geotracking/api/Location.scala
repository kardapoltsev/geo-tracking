package com.github.kardapoltsev.geotracking.api

import spray.json.DefaultJsonProtocol

case class Location(
  lat: Double,
  lon: Double,
  alt: Double,
  ts: Long,
  hacc: Double,
  vacc: Double,
  speed: Double
)
object Location extends DefaultJsonProtocol {
  implicit val _ = jsonFormat(Location.apply, "lat", "lon", "alt", "ts", "hacc", "vacc", "speed")
}
