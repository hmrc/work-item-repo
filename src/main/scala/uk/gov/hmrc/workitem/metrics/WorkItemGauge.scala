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
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.workitem.{ProcessingStatus, WorkItemRepository}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

final case class WorkItemGauge(id: String, metrics: MongoMetricsRepo, timeout: Duration)
                              (implicit ec: ExecutionContext) extends Gauge[Int] {
  override def getValue(): Int = Await.result(metrics.findCountByKey(id).map { _.getOrElse(0) }, timeout)
}

object WorkItemMetrics {
  type MetricsRefresh = () => Future[Map[String, Int]]

  def defaultGaugeIdentifier(repo: WorkItemRepository[_,_], status: ProcessingStatus): String =
    s"${repo.workItemGaugeCollectionName}.${status.name}"

  def registerGauges(repository: WorkItemRepository[_,_], metrics: MongoMetricsRepo, gaugeReadTimeout: Duration)
                    (implicit ec: ExecutionContext): Unit =
    ProcessingStatus.processingStatuses.toList.foreach { status =>
      val identifier = defaultGaugeIdentifier(repository, status)
      MetricsRegistry.defaultRegistry.register(
        identifier, WorkItemGauge(identifier, metrics, gaugeReadTimeout)
      )
    }

  def runMetrics(workItemRepo: WorkItemRepository[_, _], metricsRepo: MongoMetricsRepo)
                (implicit ec: ExecutionContext): MetricsRefresh = {
    def countThenStore(status: ProcessingStatus): Future[Option[MetricsCount]] =
      workItemRepo.count(status).flatMap { count =>
        metricsRepo.reset(MetricsCount(defaultGaugeIdentifier(workItemRepo, status), count))
      }

    () => Future.traverse(ProcessingStatus.processingStatuses.toList) { countThenStore(_) }.map {
      _.flatMap(
        _.flatMap(MetricsCount.unapply).toList
      ).toMap
    }
  }

  def apply(workItemRepository: WorkItemRepository[_, _], metrics: MongoMetricsRepo, gaugeReadTimeout: Duration)
           (implicit ec: ExecutionContext) = {
    registerGauges(workItemRepository, metrics, gaugeReadTimeout)
    runMetrics(workItemRepository, metrics)
  }

}

final case class MetricsCount(name: String, count: Int)
class MongoMetricsRepo(collectionName: String = "metrics")
                      (implicit mongo: () => DB) extends ReactiveRepository[MetricsCount, BSONObjectID](collectionName,
                                                                                                        mongo,
                                                                                                        Json.format[MetricsCount]) {

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("name" -> IndexType.Ascending), name = Some("metric_key_idx"), unique = true, background = true)
  )

  def reset(count: MetricsCount)(implicit ec: ExecutionContext) =
    collection.findAndUpdate(
      selector = Json.obj("name" -> count.name),
      update = Json.toJson(count).as[JsObject],
      upsert = true,
      fetchNewObject = true
    ).map(_.result[MetricsCount])

  def findCountByKey(key: String)(implicit ec: ExecutionContext): Future[Option[Int]] =
    collection.find(Json.obj("name" -> key)).
      one[MetricsCount].
      map(_.map(_.count))

}
