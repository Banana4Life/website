package service

import java.time.ZonedDateTime
import java.util.Arrays.asList
import javax.inject.Inject

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient
import service.CacheHelper.CacheDuration
import service.Formats._

import scala.concurrent.{ExecutionContext, Future}

class LdjamService @Inject()(conf: Configuration, cache: AsyncCacheApi, implicit val ec: ExecutionContext, ws: WSClient) {

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

  private val compileMarkdown: String => String = {
    val options = new MutableDataSet()
    options.set(EmojiExtension.USE_IMAGE_URLS, java.lang.Boolean.TRUE)
    val mdParser = Parser.builder(options).extensions(extensions).build()
    val mdRenderer = HtmlRenderer.builder(options).extensions(extensions).build()

    s => mdRenderer.render(mdParser.parse(s))
  }

  def getPosts(page: Int): Future[Seq[LdjamPost]] = {
    getPosts.map(list => list.slice(page * maxPosts, page * maxPosts + maxPosts))
  }

  private def findPostNodeIdsForUser(userid: Int): Future[Seq[Int]] = {
    cache.get[Seq[Int]](CacheHelper.jamUserFeed(userid)) flatMap {
      case Some(nodes) => Future.successful(nodes)
      case _ =>
        ws.url(s"$apiBaseUrl/vx/node/feed/$userid/author/post").get().map {
          case r if r.status == 200 =>
            val nodes = (r.json \ "feed" \\ "id").collect { case JsNumber(v) => v.toInt }
            cache.set(CacheHelper.jamUserFeed(userid), nodes, CacheDuration)
            nodes
          case _ => Nil
        }
    }
  }
  private def loadNodes(ids: Seq[Int]): Future[Seq[LdjamNode]] = {
    val cachedNodes = Future.sequence(ids.map(i => cache.get[LdjamNode](CacheHelper.jamNode(i)))).map(_.flatten)

    cachedNodes.flatMap { knownNodes =>
      val knownIds = knownNodes.map(_.id).toSet
      val leftOver = ids.filterNot(knownIds.contains)
      if (leftOver.isEmpty) Future.successful(knownNodes)
      else {
        val urlSection = leftOver.mkString("+")
        Logger.info(s"Cache missed for $urlSection, loading...")
        val res = ws.url(s"$apiBaseUrl/vx/node/get/$urlSection").get()
        res.flatMap {
          case r if r.status == 200 =>
            r.json \ "node" match {
              case JsDefined(JsArray(nodes)) =>
                val newNodes = nodes.map(_.as[LdjamNode])
                Future.sequence(newNodes.map(n => cache.set(CacheHelper.jamNode(n.id), CacheDuration)))
                    .map(_ => newNodes)
              case _ => Future.successful(knownNodes)
            }
          case _ => Future.successful(knownNodes)
        }
      }
    }
  }

  private def nodesToPosts(nodes: Seq[LdjamNode]) = {
    val (users, posts) = nodes.partition(_.`type` == "user")
    val authors = users.map(n => n.id -> n).toMap

    posts.map {n =>
        val author = authors(n.author)
        val avatar = author.meta.left.toOption.flatMap(_.avatar).map(avatar => cdnBaseUrl + avatar.substring(2) + ".32x32.fit.jpg")
        val body = compileMarkdown(n.body)
        LdjamPost(n.id, n.name, author, body, n.created, s"$pageBaseUrl${author.path}", avatar)
    }
  }

  private def nodeToJamEntry(node: LdjamNode) = {
    // TODO get authors \ "link" \ "author" - list
    // TODO cleanup body
    val r = "//(/raw/[^\\.].\\w+)".r
    val r2 = "<p>(<img[^>]+>)</p>".r
    var body = r.replaceAllIn(node.body, cdnBaseUrl + _.group(1))
    body = compileMarkdown(body)
    body = r2.replaceAllIn(body, "<div class=\"image-container\">" + _.group(1) + "</div>")
    LdJamEntry(node.id, node.name, body)
  }

  def getPosts: Future[Seq[LdjamPost]] = {
    Future.sequence(accountIds.map(findPostNodeIdsForUser))
          .map(_.flatten.sortBy(-_).take(20) ++ accountIds)
          .flatMap(loadNodes)
          .map(nodesToPosts)
  }

  def findEntry(project: Project): Future[Option[LdJamEntry]] = {
    project.jam match {
      case Some(jam) =>
        val cacheKey = CacheHelper.jamEntry(project.repoName)

        cache.get[Int](cacheKey) flatMap {
          case Some(node) => Future.successful(Some(node))
          case _ =>
            Logger.info(s"Cache missed for $cacheKey, loading...")
            ws.url(s"$apiBaseUrl/vx/node/walk/1${jam.site.getPath}").get().map {
              case r if r.status == 200 =>
                r.json \ "node" match {
                  case JsDefined(JsNumber(n)) =>
                    val id = n.toInt
                    cache.set(cacheKey, id, CacheDuration)
                    Some(id)
                  case _ => None
                }
              case _ => None
            }
        } flatMap {
          case Some(node) =>
            loadNodes(Seq(node)).map(_.headOption.map(nodeToJamEntry))
          case None =>
            Future.successful(None)
        }
      case None => Future.successful(None)
    }
  }
}

case class LdjamMeta(avatar: Option[String])

case class LdjamNode(id: Int, name: String, author: Int, body: String, created: ZonedDateTime, `type`: String, path: String, meta: Either[LdjamMeta, Int])

case class LdjamPost(id: Int, name: String, author: LdjamNode, body: String, createdAt: ZonedDateTime, authorLink: String, avatarLink: Option[String]) extends BlogPost {
  override def anchor: String = s"ldjam_$id"

  override def truncatedBody(paragraphs: Int): String = body
}

case class LdJamEntry(id: Int, name: String, body: String)
