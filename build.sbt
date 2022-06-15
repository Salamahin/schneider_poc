
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.8"

lazy val versions = new {
  val zio   = "2.0.0-RC2"
  val kafka = "3.2.0"
  val circe = "0.14.1"
}

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-encoding",
    "utf8",
    "-Xfatal-warnings",
    "-deprecation",
    "-language:postfixOps",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-feature",
    "-language:existentials",
    "-Ydelambdafy:inline"
  )
)

lazy val commonLibraries = Seq(
  "dev.zio"                    %% "zio"            % versions.zio,
  "io.d11"                     %% "zhttp"          % "2.0.0-RC3",
  "org.apache.kafka"           % "kafka-clients"   % versions.kafka,
  "org.rogach"                 %% "scallop"        % "4.1.0",
  "com.github.pureconfig"      %% "pureconfig"     % "0.17.1",
  "ch.qos.logback"             % "logback-classic" % "1.3.0-alpha12",
  "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.4",
  "io.circe"                   %% "circe-parser"   % versions.circe,
  "io.circe"                   %% "circe-generic"  % versions.circe,
  "com.ghgande"                % "j2mod"           % "3.1.1",
  "dev.zio"                    %% "zio-test"       % versions.zio % Test,
  "dev.zio"                    %% "zio-test-sbt"   % versions.zio % Test,
  "io.github.embeddedkafka"    %% "embedded-kafka" % versions.kafka % Test
)

lazy val commonAssemblySettings = Seq(
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x                             => MergeStrategy.last
  },
  assembly / test := {}
)

lazy val data_collector = project
  .settings(commonSettings: _*)
  .settings(commonAssemblySettings: _*)
  .settings(libraryDependencies ++= commonLibraries)

lazy val proxy = project
  .settings(commonSettings: _*)
  .settings(commonAssemblySettings: _*)
  .settings(libraryDependencies ++= commonLibraries)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
