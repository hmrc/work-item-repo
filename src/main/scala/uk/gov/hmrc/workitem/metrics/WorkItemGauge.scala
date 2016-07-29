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
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class WorkItemGauge(status: ProcessingStatus, metrics: MetricsRepo, atMost: Duration)
                        (implicit ec: ExecutionContext) extends Gauge[Int] {
  override def getValue(): Int = ???
    /*
    Await.result(
    metrics.find("key" -> status.n).map(_.foldRight(0) { (ms, acc) => ms.value + acc }), atMost
  )*/
}


object WorkItemGauge {

  def reset(gauges: List[WorkItemGauge], itemRepository: WorkItemRepository[_, _], metricsDB: MetricsRepo)
           (implicit ec: ExecutionContext) =
    Future.traverse(gauges) { gauge =>
      itemRepository.count(gauge.status).map { (gauge.status.name, _) }
    }.flatMap { keyValues => metricsDB.reset(MetricsStorage(values = keyValues.toMap)) }

  def createGauges(repository: WorkItemRepository[_,_], metrics: MetricsRepo, awaitTime: Duration)
                  (implicit ec: ExecutionContext): List[WorkItemGauge] =
    ProcessingStatus.processingStatuses.map(WorkItemGauge(_, metrics, awaitTime)).map { gauge =>
      MetricsRegistry.defaultRegistry.register(s"${repository.workItemGaugeCollectionName}.${gauge.status.name}", gauge)
    }.toList
}

case class MetricsStorage(id: BSONObjectID = BSONObjectID.generate, values: Map[String, Int])
object MetricsStorage {
  implicit val reads: Reads[MetricsStorage] = ???
  implicit val writes: Writes[MetricsStorage] = ???
  implicit val format: Format[MetricsStorage] = Format.apply(reads, writes)
}

class MetricsRepo(collectionName: String)(implicit mongo: () => DB) extends ReactiveRepository[MetricsStorage, BSONObjectID](collectionName, mongo, MetricsStorage.format) {

  def reset(storage: MetricsStorage)(implicit executionContext: ExecutionContext): Future[Option[MetricsStorage]] =
    collection.findAndUpdate(
      selector = Json.obj("_id" -> Json.obj("$exists" -> true)),
      update = Json.toJson(storage),
      upsert = true,
      fetchNewObject = true
    ).map(_.result)
}
