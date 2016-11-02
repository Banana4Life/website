name := """website"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  cache,
  ws,
  "com.tumblr"           %  "jumblr"                      % "0.0.11",
  "org.twitter4j"        %  "twitter4j-core"              % "4.0.5",
  "com.google.apis"      %  "google-api-services-youtube" % "v3-rev179-1.22.0",
  "org.webjars"          %% "webjars-play"                % "2.5.0-3",
  "org.webjars"          %  "jquery"                      % "3.1.1",
  "org.webjars"          %  "masonry"                     % "3.3.2"
)
