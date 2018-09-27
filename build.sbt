import PlayCrossCompilation._
import uk.gov.hmrc.DefaultBuildSettings.defaultSettings

enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)

name := "work-item-repo"

makePublicallyAvailableOnBintray := true

majorVersion                     := 6

defaultSettings()

scalaVersion := "2.11.12"

crossScalaVersions  := Seq("2.11.12", "2.12.6")

libraryDependencies ++= LibDependencies()

crossScalaVersions := Seq("2.11.8")

resolvers := Seq(
  Resolver.bintrayRepo("hmrc", "releases"),
  "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/"
)

collaborators

playCrossCompilationSettings

val collaborators = {
  pomExtra := <url>https://www.gov.uk/government/organisations/hm-revenue-customs</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git@github.com:hmrc/work-item-repo.git</connection>
      <developerConnection>scm:git@github.com:hmrc/work-item-repo.git</developerConnection>
      <url>git@github.com:hmrc/work-item-repo.git</url>
    </scm>
    <developers>
      <developer>
        <id>githubmo</id>
        <name>Mohammed Abdulrazeg</name>
        <url>http://www.equalexperts.com</url>
      </developer>
    </developers>
}