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

import sbt._

object LibDependencies {

  def apply(): Seq[ModuleID] = compile ++ test

  private val play26Version = "2.6.23"
  private val play27Version = "2.7.4"

  val compile: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    shared = Seq(
      "io.dropwizard.metrics" % "metrics-graphite"      % "3.2.5"
    ),
    play26 = Seq(
      "com.typesafe.play"     %% "play"                 % play26Version,
      "com.kenshoo"           %% "metrics-play"         % "2.6.19_0.7.0",
      "uk.gov.hmrc"           %% "metrix"               % "4.4.0-play-26"
    ),
    play27 = Seq(
      "com.typesafe.play"     %% "play"                 % play27Version,
      "com.kenshoo"           %% "metrics-play"         % "2.6.19_0.7.0",
      "uk.gov.hmrc"           %% "metrix"               % "4.4.0-play-27"
    )
  )

  val test: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    shared = Seq(
      "org.pegdown"    % "pegdown"     % "1.6.0"          % Test,
      "org.scalatest"  %% "scalatest"  % "3.0.5"          % Test
    ),
    play26 = Seq(
      "com.typesafe.play" %% "play-test"          % play26Version     % Test,
      "uk.gov.hmrc"       %% "reactivemongo-test" % "4.19.0-play-26"  % Test
    ),
    play27 = Seq(
      "com.typesafe.play" %% "play-test"          % play27Version     % Test,
      "uk.gov.hmrc"       %% "reactivemongo-test" % "4.19.0-play-27"  % Test
    )
  )
}
