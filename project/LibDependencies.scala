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

  private val play25Version = "2.5.12"
  private val play26Version = "2.6.20"

  private val compile: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    shared = Seq(
      "io.dropwizard.metrics" % "metrics-graphite"      % "3.2.5"
    ),
    play25 = Seq(
      "com.typesafe.play"     %% "play"                 % play25Version,
      "de.threedimensions"    %% "metrics-play"         % "2.5.13",
      "uk.gov.hmrc"           %% "metrix"               % "3.4.0-play-25",
      "uk.gov.hmrc"           %% "mongo-lock"           % "6.4.0-play-25",
      "uk.gov.hmrc"           %% "simple-reactivemongo" % "7.6.0-play-25"
    ),
    play26 = Seq(
      "com.kenshoo"           %% "metrics-play"         % "2.6.6_0.6.2",
      "com.typesafe.play"     %% "play"                 % play26Version,
      "uk.gov.hmrc"           %% "metrix"               % "3.4.0-play-26",
      "uk.gov.hmrc"           %% "mongo-lock"           % "6.4.0-play-26",
      "uk.gov.hmrc"           %% "simple-reactivemongo" % "7.6.0-play-26"
    )
  )

  private val test: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    shared = Seq(
      "org.pegdown"    % "pegdown"     % "1.6.0"  % Test,
      "org.scalatest"  %% "scalatest"  % "3.0.5"  % Test,
      "uk.gov.hmrc"    %% "hmrctest"   % "2.3.0"  % Test
    ),
    play25 = Seq(
      "com.typesafe.play" %% "play-test"          % play25Version   % Test,
      "uk.gov.hmrc"       %% "reactivemongo-test" % "4.4.0-play-25" % Test
    ),
    play26 = Seq(
      "com.typesafe.play" %% "play-test"          % play26Version   % Test,
      "uk.gov.hmrc"       %% "reactivemongo-test" % "4.4.0-play-26" % Test
    )
  )
}
