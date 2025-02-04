name := """website"""

organization := "banana4life"

version := "1.0"

lazy val root = (project in file("."))
    .enablePlugins(PlayScala)

scalaVersion := "3.6.3"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  caffeine,
  ws,
  specs2 % Test,
  "com.tumblr"           % "jumblr"                      % "0.0.13",
  "com.google.apis"      % "google-api-services-youtube" % "v3-rev20230822-2.0.0",
  "gov.sandia.foundry"   % "porter-stemmer"              % "1.4",
  "com.vladsch.flexmark" % "flexmark-all"                % "0.64.8",
  "org.webjars"          % "font-awesome"                % "6.7.1",
)

Compile / doc / sources := Seq.empty

// Compile / unmanagedResources / excludeFilter := new SimpleFileFilter(_.getName == "local.conf")

Compile / packageDoc / publishArtifact := false

Assets / pipelineStages := Seq(digest, gzip)

jibBaseImage := "docker.io/library/eclipse-temurin:21-jre-alpine"
jibRegistry := "ghcr.io"
jibOrganization := "banana4life"
jibName := "website"

jibMappings := (Assets / mappings).value
  .map { case (source, target) =>  (source, (Assets / WebKeys.packagePrefix).value + target) }

jibJvmFlags := "-Dplay.server.pidfile.path=/dev/null" :: Nil
