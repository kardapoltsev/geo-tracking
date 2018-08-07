name := "geo-tracking"

//import android.Keys._
//android.Plugin.androidBuild
//adb shell; settings put global location_background_throttle_package_whitelist "com.github.kardapoltsev.geotracking"
enablePlugins(AndroidApp)

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")
scalaVersion := "2.11.12"
scalacOptions in Compile += "-feature"

proguardCache in Android ++= Seq("org.scaloid")

proguardOptions in Android ++= Seq(
  //okhttp
  "-keepattributes Signature",
  "-keepattributes *Annotation*",
  "-keep class okhttp3.** { *; }",
  "-keep interface okhttp3.** { *; }",
  "-dontwarn okhttp3.**",

  "-keep class com.github.kardapoltsev.**",
  "-dontobfuscate",
  "-dontoptimize",
  "-keepattributes Signature",
  "-printseeds target/seeds.txt",
  "-printusage target/usage.txt",
  "-dontwarn okio.**",
  "-dontwarn scala.collection.**", // required from Scala 2.11.4
  "-dontwarn org.scaloid.**" // this can be omitted if current Android Build target is android-16
)

resolvers ++= Seq(
  "Google repo" at "https://maven.google.com"
)

libraryDependencies ++= Seq(
  "org.scaloid"          %% "scaloid"            % "4.2",
  "com.android.support"  %  "support-core-utils" % "27.1.1",
  "com.squareup.okhttp3" %  "okhttp"             % "3.11.0",
  "io.spray"             %% "spray-json"         % "1.3.4"
)

run <<= run in Android
install <<= install in Android
