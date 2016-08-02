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
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.workitem.{ExampleItem, ProcessingStatus, WithWorkItemRepository, WorkItemRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.JavaConverters._

class WorkItemMetricsSpec extends UnitSpec
    with ScalaFutures
    with WithWorkItemRepository
    with BeforeAndAfterAll
    with Eventually
    with LoneElement {

  override implicit val patienceConfig = PatienceConfig(timeout = 5 seconds, interval = 100 millis)

  lazy val fakeApplication = FakeApplication(additionalPlugins = Seq("com.kenshoo.play.metrics.MetricsPlugin"))

  lazy val repo2 = exampleItemRepository("items2")

  override def beforeAll() {
    super.beforeAll()
    Play.start(fakeApplication)
  }

  override def afterAll() {
    super.afterAll()
    Play.stop()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    MetricsRegistry.defaultRegistry.getGauges.asScala.foldRight(true) { case ((name, _), acc) =>
      acc && MetricsRegistry.defaultRegistry.remove(name)
    } shouldBe true
    repo2.removeAll().futureValue
  }

  "Work item metrics" should {
    "increment counts across all processing statuses when evaluated" in new SingleRepoTestCase {
      addWorkItemWithEachStatus(repo)

      refreshMetrics()().futureValue

      verifyDatabaseBackedGaugesAreAll1()
    }

    "be calculated across multiple repos for all processing status" in new MultiRepoTestCase {
      addWorkItemWithEachStatus(repo)
      addWorkItemWithEachStatus(repo2)

      val result = refreshMetrics()().futureValue

      verifyGeneratedResults(List(repo, repo2), result)
    }
  }

  trait SingleRepoTestCase {
    def refreshMetrics() = WorkItemMetrics(repo, metricsRepo, 100 milliseconds)

    def verifyDatabaseBackedGaugesAreAll1() = eventually {
      ProcessingStatus.processingStatuses.foreach { status =>
        MetricsRegistry.defaultRegistry.getGauges.get(s"items.${status.name}").getValue shouldBe 1
      }
    }
  }

  trait MultiRepoTestCase {
    def refreshMetrics() = WorkItemMetrics(List(repo, repo2), metricsRepo, 100 milliseconds)

    def verifyGeneratedResults(repos: List[WorkItemRepository[ExampleItem, BSONObjectID]], result: Map[String, Int]) =
      repos.foreach { workItemRepo =>
        ProcessingStatus.processingStatuses.foreach { status =>
          result(WorkItemMetrics.defaultGaugeIdentifier(workItemRepo, status)) shouldBe 1
        }
      }

  }

  def addWorkItemWithEachStatus(exampleItemRepository: WorkItemRepository[ExampleItem, BSONObjectID]): Unit =
    Future.traverse(ProcessingStatus.processingStatuses) { status =>
      for {
        item   <- exampleItemRepository.pushNew(item1, now)
        _      <- exampleItemRepository.markAs(item.id, status)
      } yield ()
    }.futureValue

}

