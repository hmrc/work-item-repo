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

trait MetricsSource {
  def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]]
}

final case class RepositoryBackedCachedGauge(id: String, metrics: MetricsCache)
                                       (implicit ec: ExecutionContext) extends Gauge[Int] {
  override def getValue: Int =  metrics.cache.getOrElse(id, 0)
}

trait MetricsCache {

  val cache = mutable.Map[String, Int]()

  def refreshCache(allMetrics: List[MetricsCount]) = {
    allMetrics.foreach(m => cache.put(m.name, m.count))
    val asMap: Map[String, Int] = allMetrics.map(m => m.name -> m.count).toMap
    cache.keys.foreach(key => if(!asMap.contains(key)) cache.remove(key))
  }
}

trait MongoMetricsRegistry extends MetricsCache {

  def lock: ExclusiveTimePeriodLock

  def metricsRepository: MongoMetricsRepository

  def metricsSources: List[MetricsSource]

  def metricsRegistry: MetricRegistry


  private[metrics] def updateMetricsRepository()(implicit ec: ExecutionContext): Future[Unit] = {
    Future.traverse(metricsSources) { source => source.metrics }
      .map(list => {
        val currentMetrics: Map[String, Int] = list reduce (_ ++ _)
        currentMetrics.map {
          case (key, value) => metricsRepository.update(MetricsCount(key, value))
        }
      })
  }

  def refreshAll()(implicit ec: ExecutionContext): Future[Unit] = {

    for {
      updated <- lock.tryToAcquireOrRenewLock { updateMetricsRepository }
      allMetrics <- metricsRepository.findAll(ReadPreference.secondaryPreferred)
    } yield {

      refreshCache(allMetrics)

      allMetrics
        .foreach(metric => if (!metricsRegistry.getGauges.containsKey(metric.name))
          metricsRegistry.register(metric.name, RepositoryBackedCachedGauge(metric.name, this)))

    }

  }
}
