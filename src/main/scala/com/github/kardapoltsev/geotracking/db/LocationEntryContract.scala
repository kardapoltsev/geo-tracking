package com.github.kardapoltsev.geotracking.db

import android.content.Context
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import com.github.kardapoltsev.geotracking.api.Location


//org.scaloid.Workarounds doesn't work :(
//TODO: fix column names
object LocationEntry {
  val TableName = "locations"
  val LocationColumnName =  "location"
  val _ID = "_id"
}
case class LocationEntry(id: Int, location: Location)


object LocationDbHelper {
  val DatabaseVersion = 1
  val DatabaseName = "locations.db"
}


class LocationDbHelper(implicit context: Context)
  extends SQLiteOpenHelper(
    context,
    LocationDbHelper.DatabaseName,
    null,
    LocationDbHelper.DatabaseVersion
  ) {


  override def onCreate(db: SQLiteDatabase): Unit = {
    db.execSQL(
      s"""CREATE TABLE ${LocationEntry.TableName} (
         |  ${LocationEntry._ID} INTEGER PRIMARY KEY,
         |  ${LocationEntry.LocationColumnName} TEXT NOT NULL
         |)
       """.stripMargin
    )
  }

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = { }
}