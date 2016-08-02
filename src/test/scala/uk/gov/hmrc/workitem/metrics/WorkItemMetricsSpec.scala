/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.workitem.metrics

import com.kenshoo.play.metrics.MetricsRegistry
import org.joda.time.DateTime.now
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, LoneElement}
import play.api.Play
import play.api.test.FakeApplication
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.workitem.{ProcessingStatus, WithWorkItemRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class WorkItemMetricsSpec extends UnitSpec
    with ScalaFutures
    with WithWorkItemRepository
    with BeforeAndAfterAll
    with Eventually
    with LoneElement {

  lazy val fakeApplication = FakeApplication(additionalPlugins = Seq("com.kenshoo.play.metrics.MetricsPlugin"))

  override def beforeAll() {
    super.beforeAll()
    Play.start(fakeApplication)
  }

  override def afterAll() {
    super.afterAll()
    Play.stop()
  }

  "Work item metrics" should {
    "increment counts across all processing statuses when evaluated" in {
      addWorkItemWithEachStatus()

      val result = resetMetrics()().futureValue
      verifyGeneratedResults(result)
      verifyDatabaseBackedGaugesAreAll1()
    }
  }

  def resetMetrics() = WorkItemMetrics(repo, metricsRepo, 100 milliseconds)

  def verifyDatabaseBackedGaugesAreAll1() = eventually {
    ProcessingStatus.processingStatuses.foreach { status =>
      MetricsRegistry.defaultRegistry.getGauges.get(s"items.${status.name}").getValue shouldBe 1
    }
  }

  def verifyGeneratedResults(result: Map[String, Int]) =
    ProcessingStatus.processingStatuses.foreach { status =>
      result(WorkItemMetrics.defaultGaugeIdentifier(repo, status)) shouldBe 1
    }

  def addWorkItemWithEachStatus(): Unit =
    Future.traverse(ProcessingStatus.processingStatuses) { status =>
      for {
        item   <- repo.pushNew(item1, now)
        _      <- repo.markAs(item.id, status)
      } yield ()
    }.futureValue

}

