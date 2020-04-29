import PlayCrossCompilation._
import uk.gov.hmrc.DefaultBuildSettings.defaultSettings

enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)

name := "work-item-repo"

makePublicallyAvailableOnBintray := true

majorVersion                     := 7

defaultSettings()

scalaVersion := "2.12.10"

crossScalaVersions  := Seq("2.11.12", "2.12.10")

libraryDependencies ++= LibDependencies.compile ++ LibDependencies.test

resolvers := Seq(
  Resolver.bintrayRepo("hmrc", "releases"),
  "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/"
)

playCrossCompilationSettings
