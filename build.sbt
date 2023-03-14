name := """website"""

organization := "banana4life"

version := "1.0"

lazy val root = (project in file("."))
    .enablePlugins(PlayScala)

scalaVersion := "2.13.9"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  caffeine,
  ws,
  specs2 % Test,
  "com.tumblr"           % "jumblr"                       % "0.0.13",
  "org.twitter4j"        % "twitter4j-core"               % "4.0.7",
  "com.google.apis"      % "google-api-services-youtube"  % "v3-rev20230123-2.0.0",
  "gov.sandia.foundry"   % "porter-stemmer"               % "1.4",
  "com.vladsch.flexmark" % "flexmark-all"                 % "0.64.0",
  "org.webjars"          % "font-awesome"                 % "5.15.4"
)

scalacOptions ++= Seq("-unchecked", "-deprecation")

Compile / doc / sources := Seq.empty

// Compile / unmanagedResources / excludeFilter := new SimpleFileFilter(_.getName == "local.conf")

Compile / packageDoc / publishArtifact := false

Assets / pipelineStages := Seq(digest, gzip)

jibBaseImage := "adoptopenjdk/openjdk15:x86_64-alpine-jre-15.0.2_7"

jibMappings := (Assets / mappings).value
  .map { case (source, target) =>  (source, (Assets / WebKeys.packagePrefix).value + target) }

jibJvmFlags := "-Dplay.server.pidfile.path=/dev/null" :: Nil
