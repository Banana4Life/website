resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.9")

// web plugins
addSbtPlugin("com.typesafe.sbt" % "sbt-less"   % "1.1.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")

// build
addSbtPlugin("de.gccc.sbt" % "sbt-jib" % "1.4.2")

// util
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")
