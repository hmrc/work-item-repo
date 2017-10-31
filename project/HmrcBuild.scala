/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import play.core.PlayVersion
import sbt.Keys._
import sbt._

object HmrcBuild extends Build {

  import uk.gov.hmrc.DefaultBuildSettings._
  import uk.gov.hmrc.SbtAutoBuildPlugin
  import uk.gov.hmrc.versioning.SbtGitVersioning

  val appName = "work-item-repo"

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
    .settings(scalaSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      targetJvm := "jvm-1.8",
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play"                 % PlayVersion.current % "provided",
        "uk.gov.hmrc"       %% "simple-reactivemongo" % "6.0.0",
        "uk.gov.hmrc"       %% "mongo-lock"           % "5.0.0",
        "uk.gov.hmrc"       %% "metrix"               % "1.3.0",
        "org.scalatest"     %% "scalatest"            % "2.2.6"             % "test",
        "org.pegdown"       % "pegdown"               % "1.6.0"             % "test",
        "com.typesafe.play" %% "play-test"            % PlayVersion.current % "test",
        "uk.gov.hmrc"       %% "reactivemongo-test"   % "3.0.0"             % "test",
        "uk.gov.hmrc"       %% "hmrctest"             % "2.3.0"             % "test"
      ),
      Collaborators(),
      crossScalaVersions := Seq("2.11.7", "2.10.4"),
      resolvers := Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.typesafeRepo("releases")
      )
    )
}


object Collaborators {

  def apply() = {
    pomExtra := (<url>https://www.gov.uk/government/organisations/hm-revenue-customs</url>
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
      </developers>)
  }
}
