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
  import BuildDependencies._
  import uk.gov.hmrc.DefaultBuildSettings._
  import uk.gov.hmrc.SbtAutoBuildPlugin
  import uk.gov.hmrc.versioning.SbtGitVersioning


  val appDependencies = Seq(
    play                   % "provided",
    `simple-reactivemongo`,
    `metrics-play`         % "provided",
    metrics                % "provided",
    mongoLock,
    metrix,

    scalaTest   % "test",
    pegdown     % "test",
    scalacheck  % "test",
    `play-test` % "test",
    `reactivemongo-test` % "test",
    hmrcTest
  )


  val appName = "work-item-repo"

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
    .settings(scalaSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      targetJvm := "jvm-1.8",
      libraryDependencies ++= appDependencies,
      Collaborators(),
      crossScalaVersions := Seq("2.11.7", "2.10.4")
    )
    .settings(
      resolvers := Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.typesafeRepo("releases")
      )
    )
}

private object BuildDependencies {
  val scalaTest              = "org.scalatest" %% "scalatest" % "2.2.0"
  val pegdown                = "org.pegdown" % "pegdown" % "1.4.2" cross CrossVersion.Disabled
  val scalacheck             = "org.scalacheck" %% "scalacheck" % "1.11.4"
  val play                   = "com.typesafe.play" %% "play" % PlayVersion.current
  val `play-test`            = "com.typesafe.play" %% "play-test" % PlayVersion.current
  val `simple-reactivemongo` = "uk.gov.hmrc" %% "simple-reactivemongo" % "4.8.0"
  val `reactivemongo-test`   = "uk.gov.hmrc" %% "reactivemongo-test" % "1.6.0"

  val `metrics-play`         = "com.kenshoo" %% "metrics-play" % "2.3.0_0.1.8"
  val metrics                = "com.codahale.metrics" % "metrics-graphite" % "3.0.2"
  val hmrcTest               = "uk.gov.hmrc"  %% "hmrctest" % "1.8.0" % "test"
  val mongoLock              = "uk.gov.hmrc"  %% "mongo-lock"              % "3.4.0"
  val metrix                 = "uk.gov.hmrc"  %% "metrix"              % "0.3.0"
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
