name := """website"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
    .enablePlugins(PlayScala)
    .enablePlugins(AshScriptPlugin)

scalaVersion := "2.13.1"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  ehcache,
  ws,
  guice,
  specs2 % Test,
  "com.tumblr"           %  "jumblr"                      % "0.0.13",
  "org.twitter4j"        %  "twitter4j-core"              % "4.0.7",
  "com.google.apis"      %  "google-api-services-youtube" % "v3-rev20190827-1.30.1",
  "gov.sandia.foundry"   %  "porter-stemmer"              % "1.4",
  "com.vladsch.flexmark" % "flexmark-all"                 % "0.50.40",
  "org.webjars"          %% "webjars-play"                % "2.7.3",
  "org.webjars"          %  "jquery"                      % "3.4.1",
  "org.webjars"          % "font-awesome"                 % "4.7.0"
)

scalacOptions ++= Seq("unchecked", "-deprecation")

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

bashScriptTemplateLocation := root.base / "conf" / "launch-script.sh"
