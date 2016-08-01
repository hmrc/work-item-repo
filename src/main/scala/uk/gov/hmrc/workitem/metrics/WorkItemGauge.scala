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

import com.codahale.metrics.Gauge
import com.kenshoo.play.metrics.MetricsRegistry
import play.api.libs.json._
import reactivemongo.api.DB
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.workitem.{ProcessingStatus, WorkItemRepository}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import reactivemongo.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats._

case class WorkItemGauge(status: ProcessingStatus, metrics: MongoMetricsRepo, atMost: Duration)
                        (implicit ec: ExecutionContext) extends Gauge[Int] {
  override def getValue(): Int = {
    ???
  }
    /*
    Await.result(
    metrics.find("key" -> status.n).map(_.foldRight(0) { (ms, acc) => ms.value + acc }), atMost
  )*/
}


object WorkItemGauge {

  def reset(gauges: List[WorkItemGauge], itemRepository: WorkItemRepository[_, _], metricsDB: MongoMetricsRepo)
           (implicit ec: ExecutionContext) =
    Future.traverse(gauges) { gauge =>
      itemRepository.count(gauge.status).map { (gauge.status.name, _) }
    }.flatMap { keyValues => metricsDB.reset(MetricsStorage(keyValues.toMap)) }

  def createGauges(repository: WorkItemRepository[_,_], metrics: MongoMetricsRepo, awaitTime: Duration)
                  (implicit ec: ExecutionContext): List[WorkItemGauge] =
    ProcessingStatus.processingStatuses.toList.map { status =>
      val gauge = WorkItemGauge(status, metrics, awaitTime)
      MetricsRegistry.defaultRegistry.register(s"${repository.workItemGaugeCollectionName}.${gauge.status.name}", gauge)
    }
}

case class MetricsStorage(counts: Map[String, Int])
object MetricsStorage {

  implicit val format: Format[MetricsStorage] = new Format[MetricsStorage] {
    override def reads(json: JsValue): JsResult[MetricsStorage] =
      (__ \ "counts").read[Map[String, Int]].reads(json).map { MetricsStorage(_) }

    override def writes(o: MetricsStorage): JsValue = Json.toJson(o.counts)
  }

}

class MongoMetricsRepo(collectionName: String)
                      (implicit mongo: () => DB) extends ReactiveRepository[MetricsStorage, BSONObjectID](collectionName, mongo, MetricsStorage.format) {

  def reset(storage: MetricsStorage)(implicit executionContext: ExecutionContext) =
    collection.findAndUpdate(
      selector = Json.obj("counts" -> Json.obj("$exists" -> true)),
      update = Json.obj("counts" -> storage),
      upsert = true,
      fetchNewObject = true
    ).map(_.result[MetricsStorage])

}
