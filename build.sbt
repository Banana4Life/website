name := """website"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.2"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  ehcache,
  ws,
  guice,
  "com.tumblr"           %  "jumblr"                      % "0.0.11",
  "org.twitter4j"        %  "twitter4j-core"              % "4.0.6",
  "com.google.apis"      %  "google-api-services-youtube" % "v3-rev183-1.22.0",
  "gov.sandia.foundry"   %  "porter-stemmer"              % "1.4",
  "org.webjars"          %% "webjars-play"                % "2.6.1",
  "org.webjars"          %  "jquery"                      % "3.1.1",
  "org.webjars"          % "font-awesome"                 % "4.7.0"
)
