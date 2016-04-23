enablePlugins(GatlingPlugin)

name := "keycloak-stress-tests"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.2.2",
  "com.typesafe.play" %% "play-json" % "2.5.1",
  "com.google.oauth-client" % "google-oauth-client" % "1.21.0",
  "com.google.http-client" % "google-http-client-jackson2" % "1.21.0",
  "commons-io" % "commons-io" % "2.4",
  "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.1.7" % "test",
  "io.gatling" % "gatling-test-framework" % "2.1.7" % "test"
)
