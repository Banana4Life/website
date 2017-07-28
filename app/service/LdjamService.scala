package service

import java.time.ZonedDateTime
import javax.inject.Inject

import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class LdjamService @Inject()(conf: Configuration, cache: SyncCacheApi, implicit val ec: ExecutionContext, ws: WSClient) {
  val maxPosts = 5
  var postCount = 0

  private val apiBaseUrl = conf.get[String]("ldjam.api")
  private val accountIds = conf.get[Seq[Int]]("ldjam.account")

  def getPosts(page: Int): Future[Seq[LdjamPost]] = {
    getPosts.map(list => list.slice(page * maxPosts, page * maxPosts + maxPosts))
  }

  private def findPostIdsForUser(userid: Int) = {
    ws.url(s"$apiBaseUrl/vx/node/feed/$userid/author/post").get().map { r =>
      (r.json \ "feed" \\ "id").collect { case JsNumber(v) => v.toInt }
    }
  }

  private def loadPosts(ids: Seq[Int]) = {
    val posts = ids.mkString("+")
    ws.url(s"$apiBaseUrl/vx/node/get/$posts").get().map(r => r.json \ "node").collect {
      case JsDefined(JsObject(node)) => node.values.map(e => e.as[LdjamPost]).toSeq
    }
  }

  def getPosts: Future[Seq[LdjamPost]] = {
    Future.sequence(accountIds.map(findPostIdsForUser))
          .map(_.flatten)
          .flatMap(loadPosts)
  }
}

case class LdjamPost(id: Int, name: String, body: String, created: ZonedDateTime) extends BlogPost {
  override def anchor: String = s"ldjam_$id"
  override def createdAt: ZonedDateTime = created

  override def truncatedBody(paragraphs: Int): String = body
}

object LdjamPost {
  implicit val format: Format[LdjamPost] = Json.format
}