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

import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

val appName = "work-item-repo"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    scalaVersion        := "2.11.12",
    crossScalaVersions  := Seq("2.11.12", "2.12.6"),
    targetJvm           := "jvm-1.8",
    libraryDependencies ++= Seq(
      "uk.gov.hmrc"       %% "simple-reactivemongo-26" % "0.3.0",
      "uk.gov.hmrc"       %% "mongo-lock"              % "5.2.0-SNAPSHOT",
      "uk.gov.hmrc"       %% "metrix"                  % "2.0.0-0-g0000000",
      "org.scalatest"     %% "scalatest"               % "2.2.6"             % Test,
      "org.pegdown"       % "pegdown"                  % "1.6.0"             % Test,
      "uk.gov.hmrc"       %% "reactivemongo-test"      % "3.0.0"             % Test,
      "uk.gov.hmrc"       %% "hmrctest"                % "2.3.0"             % Test
    ),
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.typesafeRepo("releases")
    )
  )
