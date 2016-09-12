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

import com.codahale.metrics.{Gauge, MetricRegistry}
import reactivemongo.api.ReadPreference
import uk.gov.hmrc.lock.ExclusiveTimePeriodLock

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

trait MetricSource {
  def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]]
}

final case class RepositoryBackedCachedGauge(id: String, metrics: MetricCache)
                                       (implicit ec: ExecutionContext) extends Gauge[Int] {
  override def getValue: Int =  metrics.cache.getOrElse(id, 0)
}

trait MetricCache {

  val cache = mutable.Map[String, Int]()

  def refreshCache(allMetrics: List[MetricCount]) = {
    allMetrics.foreach(m => cache.put(m.name, m.count))
    val asMap: Map[String, Int] = allMetrics.map(m => m.name -> m.count).toMap
    cache.keys.foreach(key => if(!asMap.contains(key)) cache.remove(key))
  }
}

trait MongoMetricRegistry extends MetricCache {

  def lock: ExclusiveTimePeriodLock

  def metricRepository: MongoMetricRepository

  def metricSources: List[MetricSource]

  def metricRegistry: MetricRegistry


  private def updateMetricRepository()(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    Future.traverse(metricSources) { source => source.metrics }
      .map(list => {
        val currentMetrics: Map[String, Int] = list reduce (_ ++ _)
        currentMetrics.map {
          case (key, value) => metricRepository.update(MetricCount(key, value))
        }
        currentMetrics
      })
  }

  def refreshAll()(implicit ec: ExecutionContext): Future[Map[String, Int]] = {

    for {
      updated <- lock.tryToAcquireOrRenewLock { updateMetricRepository }
      allMetrics <- metricRepository.findAll(ReadPreference.secondaryPreferred)
    } yield {

      refreshCache(allMetrics)

      allMetrics
        .foreach(metric => if (!metricRegistry.getGauges.containsKey(metric.name))
          metricRegistry.register(metric.name, RepositoryBackedCachedGauge(metric.name, this)))

      allMetrics.map(m => m.name -> m.count).toMap

    }

  }
}
