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

import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext

final case class MetricsCount(name: String, count: Int)

class MongoMetricsRepository(collectionName: String = "metrics")
                            (implicit mongo: () => DB) extends ReactiveRepository[MetricsCount, BSONObjectID](collectionName,
                                                                                                        mongo,
                                                                                                        Json.format[MetricsCount]) {

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("name" -> IndexType.Ascending), name = Some("metric_key_idx"), unique = true, background = true)
  )

  def update(count: MetricsCount)(implicit ec: ExecutionContext) =
    collection.findAndUpdate(
      selector = Json.obj("name" -> count.name),
      update = Json.toJson(count).as[JsObject],
      upsert = true,
      fetchNewObject = true
    ).map(_.result[MetricsCount])

}
