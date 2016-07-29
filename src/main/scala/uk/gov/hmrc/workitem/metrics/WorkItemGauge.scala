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
import uk.gov.hmrc.workitem.{ProcessingStatus, WorkItemRepository}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

abstract class WorkItemGauge(repository: WorkItemRepository[_, _]) extends Gauge[Int] {
  protected var value = 0

  def refresh(): Future[Boolean] =
    run().map { v => value = v ; true} // avoiding returning unit to improve equational reasoning

  protected def run(): Future[Int]

  def name: String

  def getValue: Int = value
}

case class WorkItemStatusGauge(status: ProcessingStatus, repository: WorkItemRepository[_, _]) extends WorkItemGauge(repository) {
  override protected def run() = repository.count(status)

  override val name = status.name
}

case class TotalWorkItemsGauge(repository: WorkItemRepository[_, _]) extends WorkItemGauge(repository) {
  override protected def run() = repository.count

  override val name = "total"
}

trait WorkItemMetrics {
  def repository: WorkItemRepository[_, _]

  def refresh(): Seq[Future[Boolean]] = gauges map { _.refresh() }

  lazy val gauges: Seq[WorkItemGauge] =
    ProcessingStatus.processingStatuses.map(WorkItemStatusGauge(_, repository)).toSeq :+ TotalWorkItemsGauge(repository)

  gauges map { gauge =>
    MetricsRegistry.defaultRegistry.register(s"${repository.workItemGaugeCollectionName}.${gauge.name}", gauge)
  }
}
