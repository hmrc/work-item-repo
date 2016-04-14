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
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpec}
import play.api.Play
import play.api.test.FakeApplication
import uk.gov.hmrc.workitem.{WorkItemRepository, ProcessingStatus, WithWorkItemRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.sequence

trait ConfiguredFakeApplication extends BeforeAndAfterAll with Eventually {
  this: Suite =>
  lazy val fakeApplication = FakeApplication(additionalPlugins = Seq("com.kenshoo.play.metrics.MetricsPlugin"))

  override def beforeAll() {
    super.beforeAll()
    Play.start(fakeApplication)
  }

  override def afterAll() {
    super.afterAll()
    Play.stop()
  }
}

class WorkItemMetricsSpec extends WordSpec
  with Matchers
  with WithWorkItemRepository
  with ConfiguredFakeApplication {

  "work item metrics" should {
    "count the number of items in a state" in {
      ProcessingStatus.processingStatuses.foreach { status =>
        updatedRegistryWith(status).futureValue
        eventually { // This is because we write to primary then read from secondary
          MetricsRegistry.defaultRegistry.getGauges.get(s"items.${status.name}").getValue shouldBe 1
        }
      }
    }

    "count the total number of items in the repo" in {
      repo.pushNew(allItems, now).futureValue
      sequence(metrics.refresh()).futureValue
      MetricsRegistry.defaultRegistry.getGauges.get("items.total").getValue shouldBe 6
    }

    def updatedRegistryWith(status: ProcessingStatus): Future[Unit] = for {
      item <- repo.pushNew(item1, now)
      _ <- repo.markAs(item.id, status)
      _ <- sequence(metrics.refresh())
    } yield ()
  }

  implicit lazy val metrics = new WorkItemMetrics {
    override implicit def repository: WorkItemRepository[_, _] = repo
  }
}
