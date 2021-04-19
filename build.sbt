name := """website"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
    .enablePlugins(PlayScala)
    .enablePlugins(AshScriptPlugin)

scalaVersion := "2.13.5"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  caffeine,
  ws,
  specs2 % Test,
  "com.tumblr"           % "jumblr"                       % "0.0.13",
  "org.twitter4j"        % "twitter4j-core"               % "4.0.7",
  "com.google.apis"      % "google-api-services-youtube"  % "v3-rev20210410-1.31.0",
  "gov.sandia.foundry"   % "porter-stemmer"               % "1.4",
  "com.vladsch.flexmark" % "flexmark-all"                 % "0.62.2",
  "org.webjars"          % "font-awesome"                 % "5.15.2"
)

scalacOptions ++= Seq("-unchecked", "-deprecation")

Compile / doc / sources := Seq.empty

Compile / packageDoc / publishArtifact := false

bashScriptTemplateLocation := root.base / "conf" / "launch-script.sh"

pipelineStages := Seq(digest, gzip)
