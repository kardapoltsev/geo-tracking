name := "geo-tracking"

import android.Keys._
android.Plugin.androidBuild

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")
scalaVersion := "2.11.8"
scalacOptions in Compile += "-feature"

proguardCache in Android ++= Seq("org.scaloid")

proguardOptions in Android ++= Seq(
  "-dontobfuscate",
  "-dontoptimize",
  "-keepattributes Signature",
  "-printseeds target/seeds.txt",
  "-printusage target/usage.txt",
  "-dontwarn scala.collection.**", // required from Scala 2.11.4
  "-dontwarn org.scaloid.**" // this can be omitted if current Android Build target is android-16
)

libraryDependencies ++= Seq(
  "org.scaloid"         %% "scaloid"            % "4.2",
  "com.android.support" %  "support-core-utils" % "24.2.0",
  "com.loopj.android"   %  "android-async-http" % "1.4.9",
  "io.spray" %%  "spray-json" % "1.3.2"
)

run <<= run in Android
install <<= install in Android
