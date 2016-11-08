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

import org.joda.time.DateTime
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{BeforeAndAfterEach, WordSpec}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json, Writes}
import reactivemongo.bson.BSONObjectID
import reactivemongo.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global

class WorkItemModuleRepositorySpec
  extends WordSpec
  with ShouldMatchers
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

      val document =
        Json.obj(
          "_id" -> _id,
          "updatedAt" -> documentCreationTime,
          "value" -> "test"
        )


      val obj: JsObject = document ++ WorkItemModuleRepository.upsertModuleQuery("testModule", workItemModuleCreationTime)
      val result = repo.collection.update[JsObject, JsObject](Json.obj("_id" -> _id),
        obj,
        upsert = true).futureValue



      val workItem = repo.pullOutstanding(documentCreationTime.plusHours(2), documentCreationTime.plusHours(2)).futureValue.get
      workItem.id shouldBe _id
      workItem.availableAt shouldBe workItemModuleCreationTime
      workItem.receivedAt shouldBe workItemModuleCreationTime
      workItem.updatedAt shouldBe timeSource.now
      workItem.status shouldBe InProgress
      workItem.item shouldBe ExampleItemWithModule(_id, documentCreationTime, "test")

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

//    "change state successfully" in {
//      val _id = BSONObjectID.generate
//      val documentCreationTime = timeSource.now
//      val workItemModuleCreationTime = documentCreationTime.plusHours(1)
//
//      val document: Seq[(String, JsValueWrapper)] = Seq(
//        "$set" -> Json.obj(
//          "_id" -> _id,
//          "updatedAt" -> documentCreationTime,
//          "value" -> "test"
//        )
//      )
//
//      repo.collection.update[JsObject, JsObject](Json.obj("_id" -> _id), Json.obj(document ++ WorkItemModuleRepository.upsertModuleQuery("testModule", workItemModuleCreationTime): _*), upsert = true).futureValue
//
//      repo.markAs(_id, Succeeded).futureValue shouldBe true
//
//      val workItem = repo.collection.find(Json.obj("_id" -> _id)).one[WorkItem[ExampleItemWithModule]](WorkItemModuleRepository.formatsOf[ExampleItemWithModule]("testModule"), global).futureValue.get
//      workItem.id shouldBe _id
//      workItem.status shouldBe Succeeded
//    }
  }

}
