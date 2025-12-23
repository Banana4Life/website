resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.10")

// web plugins
addSbtPlugin("com.github.sbt" % "sbt-less"   % "2.0.1")
addSbtPlugin("com.github.sbt" % "sbt-gzip" % "2.0.0")
addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.1.0")

// build
addSbtPlugin("de.gccc.sbt" % "sbt-jib" % "1.4.2")

// util
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")
