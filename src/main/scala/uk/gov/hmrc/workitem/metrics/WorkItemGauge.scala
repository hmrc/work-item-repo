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
import play.api.libs.json.{Format, Json}
import reactivemongo.api.DB
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.workitem.{ProcessingStatus, WorkItemRepository}
import reactivemongo.json.ImplicitBSONHandlers._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

case class WorkItemGauge(status: ProcessingStatus, itemRepository: WorkItemRepository[_, _], metrics: MetricsRepo, atMost: Duration)
                        (implicit ec: ExecutionContext) extends Gauge[Int] {
  def refresh(): Future[Int] = run().map { _.fold(0) { _.value } }

  private def run(): Future[Option[MetricsStorage]] =
    itemRepository.count(status).flatMap { value =>
      metrics.collection.findAndUpdate(
        selector = Json.obj("key" -> status.name),
        update = Json.obj("key" -> status.name, "value" -> value ),
        upsert = true,
        fetchNewObject = true
      )
    }.map { _.result[MetricsStorage] }

  override def getValue(): Int = Await.result(
    metrics.find("key" -> status).map(_.foldRight(0) { (ms, acc) => ms.value + acc }), atMost
  )
}

object WorkItemGauge {

  def createGauges(repository: WorkItemRepository[_,_], metrics: MetricsRepo, awaitTime: Duration)
                  (implicit ec: ExecutionContext): List[WorkItemGauge] =
    ProcessingStatus.processingStatuses.map(WorkItemGauge(_, repository, metrics, awaitTime)).map { gauge =>
      MetricsRegistry.defaultRegistry.register(s"${repository.workItemGaugeCollectionName}.${gauge.status.name}", gauge)
    }.toList

}

case class MetricsStorage(key: String, value: Int)
object MetricsStorage {
  implicit val format: Format[MetricsStorage] = Json.format[MetricsStorage]
}

class MetricsRepo(collectionName: String)(implicit mongo: () => DB) extends ReactiveRepository[MetricsStorage, BSONObjectID](collectionName, mongo, MetricsStorage.format)
