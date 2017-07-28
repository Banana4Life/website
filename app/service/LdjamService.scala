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

  def getPosts(page: Int): Future[Seq[LdjamPost]] = {
    getPosts.map(list => list.slice(page * maxPosts, page * maxPosts + maxPosts))
  }

  def getPosts: Future[Seq[LdjamPost]] = {
    val apiBaseUrl = conf.get[String]("ldjam.api")
    Future.sequence(
      conf.get[Seq[String]]("ldjam.account").map { userid =>
        ws.url(s"$apiBaseUrl/vx/node/feed/$userid/author/post").get().map { r =>
          (r.json \ "feed" \\ "id").collect { case JsNumber(v) => v.toInt }
        }
      }).flatMap { l =>
      val posts = l.flatten.mkString("+")
      ws.url(s"$apiBaseUrl/vx/node/get/$posts").get().map(r => r.json \ "node").collect {
        case JsDefined(JsObject(node)) => node.values.map(e => e.as[LdjamPost]).toSeq
      }
    }
  }
}

case class LdjamPost(name: String, body: String, created: ZonedDateTime)

object LdjamPost {
  implicit val format: Format[LdjamPost] = Json.format
}