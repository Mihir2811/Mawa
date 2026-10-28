// build.sbt
//
// Build definition for MAWA (Mastodon Analysis Web App).
//
// NOTE (GenAI disclosure, as required by the assignment instructions):
// The overall project skeleton (build.sbt, folder layout, plugin wiring) was
// scaffolded with help from an AI assistant (Claude) and then adapted/checked
// by the team. Prompt used (paraphrased): "set up a Play Java project with
// Guice, WSClient, JUnit/Mockito and JaCoCo for branch coverage".

name := """mawa"""
organization := "ca.concordia.soen6441"

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayJava, JacocoPlugin)

scalaVersion := "2.13.12"

libraryDependencies += guice
libraryDependencies += ws

// Mastodon post content comes back as HTML (e.g. "<p>hello <a ...>#java</a></p>").
// We're not allowed to use a Mastodon wrapper library, but Jsoup isn't one -
// it's a general-purpose HTML parser, just used here to turn that HTML into
// clean readable text for section 4.2 ("readable text rather than raw HTML").
libraryDependencies += "org.jsoup" % "jsoup" % "1.17.2"

libraryDependencies += "org.junit.jupiter" % "junit-jupiter" % "5.10.1" % Test
libraryDependencies += "org.mockito" % "mockito-core" % "5.8.0" % Test
libraryDependencies += "org.mockito" % "mockito-junit-jupiter" % "5.8.0" % Test

// sbt doesn't understand JUnit 5 (Jupiter) out of the box - it only has a
// native bridge for JUnit 4. This is the bridge that lets `sbt test` /
// `sbt testOnly` actually discover and run our @Test-annotated classes.
libraryDependencies += "net.aichler" % "jupiter-interface" % "0.11.1" % Test
testFrameworks += new TestFramework("net.aichler.jupiter.sbt.JupiterFramework")

// --- Delivery 2: Pekko Typed, for the actor-based sentiment analysis actor ---
// Play 3.0.x already pulls in pekko-actor-typed transitively (Play itself
// runs on Pekko), but we depend on it explicitly here since we use its API
// directly in app/actors. IMPORTANT: if `sbt` reports a binary-incompatible
// eviction warning for org.apache.pekko modules, change pekkoVersion below
// to whatever version `sbt evicted` says Play 3.0.1 actually resolves -
// Pekko requires every pekko-* module on the classpath to be the same
// version.
val pekkoVersion = "1.0.2"
libraryDependencies += "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion
libraryDependencies += "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test

// JaCoCo settings -> required 100% branch coverage target (see assignment 5.6)
jacocoExcludes := Seq("views.html.*", "controllers.Reverse*", "controllers.javascript.*", "router.*")

// Standard sbt targets: run / test / jacoco / javadoc / clean all work out of the box
// with the PlayJava + JacocoPlugin combo above - nothing extra to wire up.
