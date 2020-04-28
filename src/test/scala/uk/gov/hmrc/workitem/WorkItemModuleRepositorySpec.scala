/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.DateTime
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.libs.json.{JsObject, Json, Writes}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global

class WorkItemModuleRepositorySpec extends WordSpec
                                      with Matchers
                                      with ScalaFutures
                                      with BeforeAndAfterEach
                                      with IntegrationPatience
                                      with WithWorkItemRepositoryModule {
  implicit val formats = ExampleItemWithModule.formats
  implicit val dateWrites: Writes[DateTime] = ReactiveMongoFormats.dateTimeWrite

  override protected def beforeEach(): Unit = {
    repo.removeAll().futureValue
  }

  "WorkItemModuleRepository" should {
    "read the work item fields" in {
      val _id = BSONObjectID.generate
      val documentCreationTime = timeSource.now
      val workItemModuleCreationTime = documentCreationTime.plusHours(1)

      val document = Json.obj(
        "$set" -> Json.obj("_id" -> _id, "updatedAt" -> documentCreationTime, "receivedAt" -> documentCreationTime, "value" -> "test")
      ).deepMerge(WorkItemModuleRepository.upsertModuleQuery("testModule", workItemModuleCreationTime))

      repo.collection.update[JsObject, JsObject](Json.obj("_id" -> _id), document, upsert = true).
        futureValue.n shouldBe 1

      repo.pullOutstanding(documentCreationTime.plusHours(2), documentCreationTime.plusHours(2)).
        futureValue shouldBe Some(WorkItem[ExampleItemWithModule](
          _id,
          workItemModuleCreationTime,
          timeSource.now,
          workItemModuleCreationTime,
          InProgress,
          0,
          ExampleItemWithModule(_id, documentCreationTime, "test")
        )
      )
    }

    "retrieve items created after the createdAfter date when supplied" in {
      val _id = BSONObjectID.generate
      val testTime = timeSource.now
      val documentCreatedAt = testTime.minusDays(6)
      val documentAvailableAt = testTime.minusHours(1)
      val documentUpdatedAt = testTime.minusDays(2)

      val document = Json.obj(
        "$set" -> Json.obj("_id" -> _id, "updatedAt" -> documentUpdatedAt, "receivedAt" -> documentCreatedAt, "availableAt" -> documentAvailableAt, "value" -> "test")
      ).deepMerge(WorkItemModuleRepository.upsertModuleQuery("testModule", documentCreatedAt))

      repo.collection.update[JsObject, JsObject](Json.obj("_id" -> _id), document, upsert = true).
        futureValue.n shouldBe 1

      repo.pullOutstanding(testTime, testTime, Some(testTime.minusDays(7))).
        futureValue shouldBe Some(WorkItem[ExampleItemWithModule](
          _id,
          documentCreatedAt,
          timeSource.now,
          documentCreatedAt,
          InProgress,
          0,
          ExampleItemWithModule(_id, documentUpdatedAt, "test")
        )
      )
    }

    "not retrieve items created before the createdAfter date when supplied" in {
      val _id = BSONObjectID.generate
      val testTime = timeSource.now
      val documentCreatedAt = testTime.minusDays(8)
      val documentAvailableAt = testTime.minusHours(1)
      val documentUpdatedAt = testTime.minusDays(2)

      val document = Json.obj(
        "$set" -> Json.obj("_id" -> _id, "updatedAt" -> documentUpdatedAt, "receivedAt" -> documentCreatedAt, "availableAt" -> documentAvailableAt, "value" -> "test")
      ).deepMerge(WorkItemModuleRepository.upsertModuleQuery("testModule", documentCreatedAt))

      repo.collection.update[JsObject, JsObject](Json.obj("_id" -> _id), document, upsert = true).
        futureValue.n shouldBe 1

      repo.pullOutstanding(testTime, testTime, Some(testTime.minusDays(7))).
        futureValue shouldBe None
    }

    "never update T" in {
      intercept[IllegalStateException] {
        repo.pushNew(ExampleItemWithModule(BSONObjectID.generate, timeSource.now, "test"), timeSource.now)
      }.getMessage shouldBe "The model object cannot be created via the work item module repository"

      intercept[IllegalStateException] {
        val m = ExampleItemWithModule(BSONObjectID.generate, timeSource.now, "test")
        WorkItemModuleRepository.formatsOf[ExampleItemWithModule]("testModule").writes(WorkItem(BSONObjectID.generate, timeSource.now, timeSource.now, timeSource.now, ToDo, 0, m))
      }.getMessage shouldBe "A work item module is not supposed to be written"

    }

    "use the module name as the gauge name" in {
      repo.metricPrefix should be ("testModule")
    }

    "change state successfully" in {
      implicit val fmt = WorkItemModuleRepository.formatsOf[ExampleItemWithModule]("testModule")
      val _id = BSONObjectID.generate
      val documentCreationTime = timeSource.now
      val workItemModuleCreationTime = documentCreationTime.plusHours(1)

      val document = Json.obj(
        "$set" -> Json.obj("_id" -> _id, "updatedAt" -> documentCreationTime, "value" -> "test")
      ).deepMerge(WorkItemModuleRepository.upsertModuleQuery("testModule", workItemModuleCreationTime))

      repo.collection.update[JsObject, JsObject](Json.obj("_id" -> _id), document, upsert = true).
        futureValue.n shouldBe 1

      repo.markAs(_id, Succeeded).futureValue shouldBe true

      val Some(workItem: WorkItem[ExampleItemWithModule]) =
        repo.collection.find(Json.obj("_id" -> _id)).one[WorkItem[ExampleItemWithModule]].futureValue
      workItem.id shouldBe _id
      workItem.status shouldBe Succeeded
    }
  }

}
