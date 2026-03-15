val scala3Version = "3.3.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "scala-service",
    version := "0.1.0",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked"
    )
  )
