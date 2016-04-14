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

package uk.gov.hmrc.workitem

import org.joda.time.chrono.ISOChronology
import org.joda.time.{LocalDate, DateTime, Duration}
import org.scalatest.{Suite, BeforeAndAfterEach}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.json.BSONFormats
import uk.gov.hmrc.mongo.MongoSpecSupport
import scala.concurrent.ExecutionContext.Implicits.global


trait WithWorkItemRepository extends ScalaFutures
  with MongoSpecSupport
  with BeforeAndAfterEach
  with IntegrationPatience {
    this: Suite =>

  val timeSource = new {
    var now: DateTime = DateTime.now(ISOChronology.getInstanceUTC)

    def advanceADay() = setNowTo(now.plusDays(1))

    def advance(duration: Duration) = setNowTo(now.plus(duration))

    def retreat1Day() = setNowTo(now.minusDays(1))

    def retreatAlmostDay() = setNowTo(now.minusDays(1).plusMinutes(1))

    def setNowTo(newNow: DateTime) = {
      now = newNow
      now
    }
  }

  case class ExampleItem(id: String)

  object ExampleItem {
    implicit val format = Json.format[ExampleItem]
  }

  import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.objectIdFormats

  lazy val repo = new WorkItemRepository[ExampleItem, BSONObjectID](
    collectionName = "items",
    mongo = mongo,
    itemFormat = WorkItem.workItemMongoFormat[ExampleItem]) {

    override lazy val inProgressRetryAfter: Duration = Duration.standardHours(1)

    def inProgressRetryAfterProperty: String = ???

    def now: DateTime = timeSource.now

    def workItemFields: WorkItemFieldNames = new WorkItemFieldNames {
      val receivedAt = "receivedAt"
      val updatedAt = "updatedAt"
      val availableAt = "availableAt"
      val status = "status"
      val id = "_id"
      val failureCount = "failureCount"
    }
  }

  protected override def beforeEach() {
    import scala.concurrent.ExecutionContext.Implicits.global
    repo.removeAll().futureValue
  }

  val today = LocalDate.now(ISOChronology.getInstanceUTC)
  val yesterday = today.minusDays(1)

  val item1 = ExampleItem("id1")
  val item2 = item1.copy(id = "id2")
  val item3 = item1.copy(id = "id3")
  val item4 = item1.copy(id = "id4")
  val item5 = item1.copy(id = "id5")
  val item6 = item1.copy(id = "id6")

  val allItems = Seq(item1, item2, item3, item4, item5, item6)
}
