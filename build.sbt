name := """website"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  "com.tumblr"    %  "jumblr"         % "0.0.10",
  "org.twitter4j" %  "twitter4j-core" % "4.0.2",
  "org.webjars"   %% "webjars-play"   % "2.3.0-2",
  "org.webjars"   %  "jquery"         % "2.1.1",
  "org.webjars"   %  "masonry"        % "3.1.5"
)
