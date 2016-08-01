package uk.gov.hmrc.workitem.metrics

import org.scalatest.LoneElement
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.workitem.ToDo
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

class MetricsRepoSpec extends UnitSpec with MongoSpecSupport with ScalaFutures with LoneElement {
  override implicit val patienceConfig = PatienceConfig(timeout = 30 seconds, interval = 100 millis)

  lazy val metricsRepo = new MongoMetricsRepo(databaseName)

  "reset" should {
    "store the provided MetricsStorage instance with the 'counts' key" in {
      val storage = MetricsStorage(
        Map(ToDo.name -> 5)
      )

      metricsRepo.reset(storage).futureValue

      metricsRepo.findAll().futureValue.loneElement shouldBe storage
    }
  }

}
