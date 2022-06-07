ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  "dev.zio"                 %% "zio"            % "2.0.0-RC2",
  "dev.zio"                 %% "zio-test"       % "2.0.0-RC2" % Test,
  "dev.zio"                 %% "zio-test-sbt"   % "2.0.0-RC2" % Test,
  "io.github.embeddedkafka" %% "embedded-kafka" % "3.2.0" % Test,
  "io.d11"                  %% "zhttp"          % "2.0.0-RC3",
  "org.apache.kafka"        % "kafka-clients"   % "3.2.0",
  "org.rogach"              %% "scallop"        % "4.1.0",
  "ch.qos.logback"             % "logback-classic"         % "1.3.0-alpha12",
  "com.typesafe.scala-logging" %% "scala-logging"          % "3.9.4",
)

lazy val root = (project in file("."))
  .settings(
    name := "schneider_poc"
  )

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")