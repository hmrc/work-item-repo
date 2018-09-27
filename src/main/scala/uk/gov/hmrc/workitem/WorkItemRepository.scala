/*
 * Copyright 2018 HM Revenue & Customs
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

import com.typesafe.config.Config
import org.joda.time.{DateTime, Duration}
import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.metrix.domain.MetricSource
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

abstract class WorkItemRepository[T, ID](collectionName: String,
                                         mongo: () => DB,
                                         itemFormat: Format[WorkItem[T]],
                                         config: Config
                                        )(implicit idFormat: Format[ID], mfItem: Manifest[T], mfID: Manifest[ID])
  extends ReactiveRepository[WorkItem[T], ID](collectionName, mongo, itemFormat, idFormat)
  with Operations.Cancel[ID]
  with Operations.FindById[ID, T]
  with MetricSource {

  def now: DateTime

  def workItemFields: WorkItemFieldNames

  def inProgressRetryAfterProperty: String

  def metricPrefix: String = collectionName

  override def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    Future.traverse(ProcessingStatus.processingStatuses.toList) { status =>
      count(status).map(value => s"$metricPrefix.${status.name}" -> value)
    }.map(_.toMap)
  }

  private implicit val dateFormats: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  lazy val inProgressRetryAfter: Duration = Duration.millis(
    config.getLong(inProgressRetryAfterProperty)
  )

  private def newWorkItem(receivedAt: DateTime, availableAt: DateTime, initialState: T => ProcessingStatus)(item: T) = WorkItem(
    id = BSONObjectID.generate,
    receivedAt = receivedAt,
    updatedAt = now,
    availableAt = availableAt,
    status = initialState(item),
    failureCount = 0,
    item = item
  )

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq(workItemFields.status -> IndexType.Ascending, workItemFields.updatedAt -> IndexType.Ascending),
      unique = false,
      background = true),
    Index(
      key = Seq(workItemFields.status -> IndexType.Ascending, workItemFields.availableAt -> IndexType.Ascending),
      unique = false,
      background = true),
    Index(
      key = Seq(workItemFields.status -> IndexType.Ascending),
      unique = false,
      background = true)
  )

  private def toDo(item: T): ProcessingStatus = ToDo

  def pushNew(item: T, receivedAt: DateTime)(implicit ec: ExecutionContext): Future[WorkItem[T]] = pushNew(item, receivedAt, toDo _)

  def pushNew(item: T, receivedAt: DateTime, initialState: T => ProcessingStatus)(implicit ec: ExecutionContext): Future[WorkItem[T]] = pushNew(item, receivedAt, receivedAt, initialState)

  def pushNew(item: T, receivedAt: DateTime, availableAt: DateTime)(implicit ec: ExecutionContext): Future[WorkItem[T]] = pushNew(item, receivedAt, availableAt, toDo _)

  def pushNew(item: T, receivedAt: DateTime, availableAt: DateTime, initialState: T => ProcessingStatus)(implicit ec: ExecutionContext): Future[WorkItem[T]] = {
    val workItem = newWorkItem(receivedAt, availableAt, initialState)(item)
    insert(workItem).map(_ => workItem)
  }

  def pushNew(items: Seq[T], receivedAt: DateTime)(implicit ec: ExecutionContext): Future[Seq[WorkItem[T]]] = pushNew(items, receivedAt, toDo _)

  def pushNew(items: Seq[T], receivedAt: DateTime, initialState: T => ProcessingStatus)(implicit ec: ExecutionContext): Future[Seq[WorkItem[T]]] = pushNew(items, receivedAt, receivedAt, initialState)

  def pushNew(items: Seq[T], receivedAt: DateTime, availableAt: DateTime, initialState: T => ProcessingStatus)(implicit ec: ExecutionContext): Future[Seq[WorkItem[T]]] = {
    val workItems = items.map(newWorkItem(receivedAt, availableAt, initialState))

    bulkInsert(workItems).map { savedCount =>
      if (savedCount.n == workItems.size) workItems
      else throw new RuntimeException(s"Only $savedCount items were saved")
    }
  }

  private case class IdList(_id : BSONObjectID)
  private implicit val read: Reads[IdList] = {
    implicit val objectIdReads: Reads[BSONObjectID] = ReactiveMongoFormats.objectIdRead
    Json.reads[IdList]
  }

  def pullOutstanding(failedBefore: DateTime, availableBefore: DateTime)(implicit ec: ExecutionContext): Future[Option[WorkItem[T]]] = {

    def getWorkItem(idList: IdList): Future[Option[WorkItem[T]]] = {
      import ReactiveMongoFormats.objectIdWrite
      collection.find(
        selector = Json.obj(workItemFields.id -> idList._id),
        projection = None
      ).one[WorkItem[T]]
    }

    val id = findNextItemId(failedBefore, availableBefore)
    id.map(_.map(getWorkItem)).flatMap(_.getOrElse(Future.successful(None)))
  }

  private def findNextItemId(failedBefore: DateTime, availableBefore: DateTime)(implicit ec: ExecutionContext) : Future[Option[IdList]] = {

    def findNextItemIdByQuery(query: JsObject)(implicit ec: ExecutionContext): Future[Option[IdList]] =
      findAndUpdate(
        query = query,
        update = setStatusOperation(InProgress, None),
        fetchNewObject = true,
        fields = Some(Json.obj(workItemFields.id -> 1))
      ).map(
        _.value.map(Json.toJson(_).as[IdList])
      )

    def todoQuery(failedBefore: DateTime, availableBefore: DateTime): JsObject = {
      Json.obj(workItemFields.status -> ToDo,       workItemFields.availableAt -> Json.obj("$lt" -> availableBefore))
    }

    def failedQuery(failedBefore: DateTime, availableBefore: DateTime): JsObject = {
      Json.obj("$or" -> Seq(
        Json.obj(workItemFields.status -> Failed,     workItemFields.updatedAt -> Json.obj("$lt" -> failedBefore), workItemFields.availableAt -> Json.obj("$lt" -> availableBefore)),
        Json.obj(workItemFields.status -> Failed,     workItemFields.updatedAt -> Json.obj("$lt" -> failedBefore), workItemFields.availableAt -> Json.obj("$exists" -> false))
      ))
    }

    def inProgressQuery(failedBefore: DateTime, availableBefore: DateTime): JsObject = {
      Json.obj(workItemFields.status -> InProgress, workItemFields.updatedAt -> Json.obj("$lt" -> now.minus(inProgressRetryAfter)))
    }

    findNextItemIdByQuery(todoQuery(failedBefore, availableBefore)).flatMap {
      case None => findNextItemIdByQuery(failedQuery(failedBefore, availableBefore)).flatMap {
        case None => findNextItemIdByQuery(inProgressQuery(failedBefore, availableBefore))
        case item => Future.successful(item)
      }
      case item => Future.successful(item)
    }
  }

  def markAs(id: ID, status: ProcessingStatus, availableAt: Option[DateTime] = None)(implicit ec: ExecutionContext): Future[Boolean] =
    collection.update(
      selector = Json.obj(workItemFields.id -> id),
      update = setStatusOperation(status, availableAt)
    ).map(_.n > 0)

  def complete(id: ID, newStatus: ProcessingStatus with ResultStatus)(implicit ec: ExecutionContext): Future[Boolean] = {
    collection.update(
      selector = Json.obj(workItemFields.id -> id, workItemFields.status -> InProgress),
      update = setStatusOperation(newStatus, None)
    ).map(_.nModified > 0)
  }

  def cancel(id: ID)(implicit ec: ExecutionContext): Future[StatusUpdateResult] = {
    import uk.gov.hmrc.workitem.StatusUpdateResult._
    findAndUpdate(
      query = Json.obj(
        workItemFields.id -> id,
        workItemFields.status -> Json.obj("$in" -> List(ToDo, Failed, PermanentlyFailed, Ignored, Duplicate, Deferred)) // TODO we should be able to express the valid to/from states in traits of ProcessingStatus
      ),
      update = setStatusOperation(Cancelled, None),
      fetchNewObject = false
    ).flatMap { res =>
      res.value match {
        case Some(item) => Future.successful(Updated(
          previousStatus = Json.toJson(item).\(workItemFields.status).as[ProcessingStatus],
          newStatus = Cancelled
        ))
        case None => findById(id).map {
          case Some(item) => NotUpdated(item.status)
          case None => NotFound
        }
      }
    }
  }

  def count(state: ProcessingStatus)(implicit ec: ExecutionContext): Future[Int] =
    count(Json.obj(workItemFields.status -> state.name), ReadPreference.secondaryPreferred)

  private def setStatusOperation(newStatus: ProcessingStatus, availableAt: Option[DateTime]): JsObject = {
    val fields = Json.obj(
      workItemFields.status -> newStatus,
      workItemFields.updatedAt -> now
    ) ++ availableAt.map(when => Json.obj(workItemFields.availableAt -> when)).getOrElse(Json.obj())

    val statusUpdate = Json.obj("$set" -> fields)
    if (newStatus == Failed) statusUpdate ++ Json.obj("$inc" -> Json.obj(workItemFields.failureCount -> 1))
    else statusUpdate
  }

}
object Operations {
  trait Cancel[ID] {
    def cancel(id: ID)(implicit ec: ExecutionContext): Future[StatusUpdateResult]
  }
  trait FindById[ID, T] {
    def findById(id: ID, readPreference : reactivemongo.api.ReadPreference)(implicit ec: ExecutionContext): Future[Option[WorkItem[T]]]
  }
}
