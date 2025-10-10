name := """website"""

organization := "banana4life"

version := "1.0"

lazy val root = (project in file("."))
    .enablePlugins(PlayScala)

scalaVersion := "3.7.3"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  caffeine,
  ws,
  specs2 % Test,
  "gov.sandia.foundry"   % "porter-stemmer"              % "1.4",
  "com.vladsch.flexmark" % "flexmark-all"                % "0.64.8",
  "org.webjars"          % "font-awesome"                % "7.0.1",
  "com.dripower"        %% "play-circe"                  % "3014.1",
  "io.valkey"            % "valkey-java"                 % "5.5.0",
)

Compile / doc / sources := Seq.empty

// Compile / unmanagedResources / excludeFilter := new SimpleFileFilter(_.getName == "local.conf")

Compile / packageDoc / publishArtifact := false

// only in runProd (stage)
pipelineStages := Seq(digest, gzip)
// jibImageBuild does not run stage - but this would run in dev mode too so
// only in play.env=prod
Assets / pipelineStages := {
  if (sys.props.get("play.env").contains("prod")) Seq(digest, gzip) else Nil
}

jibBaseImage := "docker.io/library/eclipse-temurin:21-jre-alpine"
jibRegistry := "ghcr.io"
jibOrganization := "banana4life"
jibName := "website"

jibMappings := (Assets / mappings).value
  .map { case (source, target) =>  (source, (Assets / WebKeys.packagePrefix).value + target) }

jibJvmFlags := "-Dplay.server.pidfile.path=/dev/null" :: Nil
