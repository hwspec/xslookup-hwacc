// See README.md for license details.

ThisBuild / scalaVersion     := "2.13.18"
ThisBuild / version          := "0.4.0"
ThisBuild / organization     := "kzt.me"

val chiselVersion = "7.6.0"
val scalatestVersion = "3.2.18"

Test / parallelExecution := false
// parallel execusion of 'sbt test' of this project might fail.
// this may be related to java-11
// with java-17, parallel execusion seems to work.

lazy val root = (project in file("."))
  .settings(
    name := "xskernel-chisel",

    Compile / unmanagedSourceDirectories ++= Seq(
      baseDirectory.value / "rial-tmpfix" / "src" / "main" / "scala",
      baseDirectory.value / "berkeley-hardfloat" / "hardfloat" / "src" / "main" / "scala"
    ),

    libraryDependencies ++= Seq(
      "net.java.dev.jna" % "jna" % "5.10.0",
      "net.java.dev.jna" % "jna-platform" % "5.10.0",
      "org.jfree" % "jfreechart" % "1.5.0",
      "org.apache.commons" % "commons-math3" % "3.2",
      "org.typelevel" %% "spire" % "0.17.0",
      "org.scala-lang" % "scala-compiler" % "2.13.16",
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
      //
      "org.chipsalliance" %% "chisel" % chiselVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion % "test",
    ),
    scalacOptions ++= Seq(
     "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations",
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full),
  )
