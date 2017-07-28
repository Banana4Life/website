package service

import java.time.ZonedDateTime
import java.util.Arrays.asList
import javax.inject.Inject

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class LdjamService @Inject()(conf: Configuration, cache: SyncCacheApi, implicit val ec: ExecutionContext, ws: WSClient) {
  val maxPosts = 5
  var postCount = 0

  private val cdnBaseUrl = conf.get[String]("ldjam.cdn")
  private val pageBaseUrl = conf.get[String]("ldjam.site")
  private val apiBaseUrl = conf.get[String]("ldjam.api")
  private val accountIds = conf.get[Seq[Int]]("ldjam.account")

  private val extensions = asList(
    AutolinkExtension.create(),
    EmojiExtension.create()
  )
  private val options = new MutableDataSet()
  options.set(EmojiExtension.USE_IMAGE_URLS, java.lang.Boolean.TRUE)
  private val mdParser = Parser.builder(options).extensions(extensions).build()
  private val mdRenderer = HtmlRenderer.builder(options).extensions(extensions).build()

  def getPosts(page: Int): Future[Seq[LdjamPost]] = {
    getPosts.map(list => list.slice(page * maxPosts, page * maxPosts + maxPosts))
  }

  private def findPostNodeIdsForUser(userid: Int) = {
    ws.url(s"$apiBaseUrl/vx/node/feed/$userid/author/post").get().map { r =>
      (r.json \ "feed" \\ "id").collect { case JsNumber(v) => v.toInt }
    }
  }
  private def loadNodes(ids: Seq[Int]) = {
    val posts = ids.mkString("+")
    ws.url(s"$apiBaseUrl/vx/node/get/$posts").get().map(r => r.json \ "node").collect {
      case JsDefined(JsArray(nodes)) => nodes.map(_.as[LdjamNode])
    }
  }

  private def nodesToPosts(nodes: Seq[LdjamNode]) = {
    val (users, posts) = nodes.partition(_.`type` == "user")
    val authors = users.map(n => n.id -> n).toMap

    posts.map {n =>
        val author = authors(n.author)
        val avatar = author.meta.left.toOption.map(m => cdnBaseUrl + m.avatar.substring(2) + ".32x32.fit.jpg")
        val body = mdRenderer.render(mdParser.parse(n.body))
        LdjamPost(n.id, n.name, author, body, n.created, s"$pageBaseUrl${author.path}", avatar)
    }
  }

  def getPosts: Future[Seq[LdjamPost]] = {
    Future.sequence(accountIds.map(findPostNodeIdsForUser))
          .map(_.flatten.sortBy(-_).take(20) ++ accountIds)
          .flatMap(loadNodes)
          .map(nodesToPosts)
  }
}

case class LdjamMeta(avatar: String)

case class LdjamNode(id: Int, name: String, author: Int, body: String, created: ZonedDateTime, `type`: String, path: String, meta: Either[LdjamMeta, Int])

case class LdjamPost(id: Int, name: String, author: LdjamNode, body: String, createdAt: ZonedDateTime, authorLink: String, avatarLink: Option[String]) extends BlogPost {
  override def anchor: String = s"ldjam_$id"

  override def truncatedBody(paragraphs: Int): String = body
}

object LdjamNode {
  val metaFormat: Format[LdjamMeta] = Json.format

  object MetaFormat extends Format[Either[LdjamMeta, Int]] {
    override def writes(o: Either[LdjamMeta, Int]): JsValue = o match {
      case Left(meta) => metaFormat.writes(meta)
      case _ => JsArray()
    }

    override def reads(json: JsValue): JsResult[Either[LdjamMeta, Int]] = json match {
      case JsArray(_) => JsSuccess(Right(1))
      case o: JsObject => metaFormat.reads(o).map(Left(_))
      case _ => JsError("LdjamMeta must be an empty array or an object!")
    }
  }

  implicit val format: Format[LdjamNode] = {
    implicit val meta = MetaFormat
    Json.format
  }
}