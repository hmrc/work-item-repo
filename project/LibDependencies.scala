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

  private val play26Version = "2.6.25"
  private val play27Version = "2.7.9"
  private val play28Version = "2.8.7"

  val compile: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    play26 = Seq(
      "uk.gov.hmrc"           %% "metrix"               % "5.0.0-play-26"
    ),
    play27 = Seq(
      "uk.gov.hmrc"           %% "metrix"               % "5.0.0-play-27"
    ),
    play28 = Seq(
      "uk.gov.hmrc"           %% "metrix"               % "5.0.0-play-26"
    )
  )

  val test: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    shared = Seq(
      "org.scalatest"        %% "scalatest"          % "3.1.4"       % Test,
      "com.vladsch.flexmark" %  "flexmark-all"       % "0.36.8"      % Test
    ),
    play26 = Seq(
      "com.typesafe.play"    %% "play-test"          % play26Version   % Test,
      "uk.gov.hmrc"          %% "reactivemongo-test" % "5.0.0-play-26" % Test,
    ),
    play27 = Seq(
      "com.typesafe.play"    %% "play-test"          % play27Version   % Test,
      "uk.gov.hmrc"          %% "reactivemongo-test" % "5.0.0-play-27" % Test,
    ),
    play28 = Seq(
      "com.typesafe.play"    %% "play-test"          % play28Version   % Test,
      "uk.gov.hmrc"          %% "reactivemongo-test" % "5.0.0-play-28" % Test,
    )
  )
}
