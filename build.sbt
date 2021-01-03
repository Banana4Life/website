name := """website"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
    .enablePlugins(PlayScala)
    .enablePlugins(AshScriptPlugin)

scalaVersion := "2.13.4"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  caffeine,
  ws,
  specs2 % Test,
  "com.tumblr"           %  "jumblr"                      % "0.0.13",
  "org.twitter4j"        %  "twitter4j-core"              % "4.0.7",
  "com.google.apis"      %  "google-api-services-youtube" % "v3-rev20201214-1.31.0",
  "gov.sandia.foundry"   %  "porter-stemmer"              % "1.4",
  "com.vladsch.flexmark" % "flexmark-all"                 % "0.62.2",
  "org.webjars"          % "font-awesome"                 % "5.15.1"
)

scalacOptions ++= Seq("-unchecked", "-deprecation")

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

bashScriptTemplateLocation := root.base / "conf" / "launch-script.sh"
