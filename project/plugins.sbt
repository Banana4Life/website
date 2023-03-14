resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.19")

// web plugins
addSbtPlugin("com.typesafe.sbt" % "sbt-less"   % "1.1.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")

// build
addSbtPlugin("de.gccc.sbt" % "sbt-jib" % "1.0.1")
