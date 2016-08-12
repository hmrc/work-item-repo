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

import org.joda.time.{DateTime, Duration}
import play.api.Play
import play.api.libs.json._
import reactivemongo.api.{ReadPreference, DB}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.core.commands._
import reactivemongo.json.BSONFormats
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

abstract class WorkItemRepository[T, ID](collectionName: String,
                                         mongo: () => DB,
                                         itemFormat: Format[WorkItem[T]]
                                        )(implicit idFormat: Format[ID], mfItem: Manifest[T], mfID: Manifest[ID])
  extends ReactiveRepository[WorkItem[T], ID](collectionName, mongo, itemFormat, idFormat)
  with Operations.Cancel[ID]
  with Operations.FindById[ID, T] {

  def now: DateTime

  def workItemFields: WorkItemFieldNames

  def inProgressRetryAfterProperty: String

  def workItemGaugeCollectionName = collectionName

  private implicit val dateFormats = ReactiveMongoFormats.dateTimeFormats
  private implicit val bsonFormatter = BSONFormats.BSONDocumentFormat

  lazy val inProgressRetryAfter: Duration = {
    implicit val app = Play.current
    val configValue = Play.application.configuration.
      getMilliseconds(inProgressRetryAfterProperty).
      getOrElse(throw new IllegalStateException(s"$inProgressRetryAfterProperty config value not set"))
    Duration.millis(configValue)
  }

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
    collection.save(Json.toJson(workItem)).map(_ => workItem)
  }

  def pushNew(items: Seq[T], receivedAt: DateTime)(implicit ec: ExecutionContext): Future[Seq[WorkItem[T]]] = pushNew(items, receivedAt, toDo _)

  def pushNew(items: Seq[T], receivedAt: DateTime, initialState: T => ProcessingStatus)(implicit ec: ExecutionContext): Future[Seq[WorkItem[T]]] = pushNew(items, receivedAt, receivedAt, initialState)

  def pushNew(items: Seq[T], receivedAt: DateTime, availableAt: DateTime, initialState: T => ProcessingStatus)(implicit ec: ExecutionContext): Future[Seq[WorkItem[T]]] = {
    val workItems = items.map(newWorkItem(receivedAt, availableAt, initialState))
    val stream: Stream[collection.pack.Document] = workItems.map(wi => Json.toJson(wi).as[JsObject]).toStream
    collection.db.connection.waitForPrimary(3 seconds) flatMap { _ =>
      collection.bulkInsert(stream, ordered = false).map { savedCount =>
        if (savedCount.n == workItems.size) workItems
        else throw new RuntimeException(s"Only $savedCount items were saved")
      }
    }
  }

  private case class IdList(_id : BSONObjectID)
  private implicit val read = Json.reads[IdList]

  def pullOutstanding(failedBefore: DateTime, availableBefore: DateTime)(implicit ec: ExecutionContext): Future[Option[WorkItem[T]]] = {

    def getWorkItem(idList : IdList): Future[Option[WorkItem[T]]] = {
      val document = collection.find(selector = Json.obj(workItemFields.id -> idList._id)).one[BSONDocument]
      document.map(_.map(Json.toJson(_).as[WorkItem[T]]))
    }
    val id = findNextItemId(failedBefore,availableBefore)
    id.map(_.map(getWorkItem(_))).flatMap(_.getOrElse(Future.successful(None)))
  }

  private def findNextItemId(failedBefore: DateTime, availableBefore: DateTime)(implicit ec: ExecutionContext) : Future[Option[IdList]] = {

    def findNextItemIdByQuery(query: JsObject)(implicit ec: ExecutionContext): Future[Option[IdList]] = {
      collection.db.command(
        FindAndModify(
          collection = collection.name,
          query = query.as[BSONDocument],
          fields = Some(Json.obj(workItemFields.id -> 1).as[BSONDocument]),
          modify = Update(setStatusOperation(InProgress, None).as[BSONDocument], fetchNewObject = true)
        )
      ).map(_.map(Json.toJson(_).as[IdList]))
    }

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

    findNextItemIdByQuery(todoQuery(failedBefore, availableBefore)).flatMap( item => item match {
      case Some(_) => Future.successful(item)
      case None => findNextItemIdByQuery(failedQuery(failedBefore, availableBefore)).flatMap( item2 => item2 match {
        case Some(value) => Future.successful(item2)
        case None => findNextItemIdByQuery(inProgressQuery(failedBefore, availableBefore))
      })
    })
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
    collection.db.command(FindAndModify(
      collection = collection.name,
      query = Json.obj(
        workItemFields.id -> id,
        workItemFields.status -> Json.obj("$in" -> List(ToDo, Failed, PermanentlyFailed, Ignored, Duplicate, Deferred)) // TODO we should be able to express the valid to/from states in traits of ProcessingStatus
      ).as[BSONDocument],
      modify = Update(setStatusOperation(Cancelled, None).as[BSONDocument], fetchNewObject = false)
    )).flatMap {
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

  def count(state: ProcessingStatus)(implicit ec: ExecutionContext): Future[Int] = collection.db.command(
    Count(collection.name, Some(BSONDocument(workItemFields.status -> state.name))), ReadPreference.secondaryPreferred)

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
