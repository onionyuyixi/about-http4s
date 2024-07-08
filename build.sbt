ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"
val Http4sVersion = "0.22.0"

lazy val root = (project in file("."))
  .settings(
    name := "about-hhtp4s"
  ).settings(
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "ch.qos.logback"  %  "logback-classic"     % "1.5.6",
      "io.getquill" %% "quill-jasync-postgres" % "4.8.0",
      "org.testcontainers" % "postgresql" % "1.19.8",
      "org.postgresql" % "postgresql" % "42.7.3",
    )
  )
