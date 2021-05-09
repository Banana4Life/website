package service

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.Arrays.asList
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.emoji.{EmojiExtension, EmojiImageType}
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import play.api.cache.AsyncCacheApi
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Configuration, Logger}
import service.CacheHelper.CacheDuration

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class LdjamService(conf: Configuration, cache: AsyncCacheApi, implicit val ec: ExecutionContext, ws: WSClient) {

    val MaxLimit = 250

    private val logger = Logger(classOf[LdjamService])

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
        options.set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_ONLY)
        val mdParser = Parser.builder(options).extensions(extensions).build()
        val mdRenderer = HtmlRenderer.builder(options).extensions(extensions).build()

        s => mdRenderer.render(mdParser.parse(s))
    }

    private def request(url: String): WSRequest =
        ws.url(url)
            .withHttpHeaders("User-Agent" -> "Banana4Life")
            .withRequestTimeout(5.seconds)

    private def get[T <: ApiResponse: Reads](url: String): Future[T] = {
        logger.info(url)
        request(url).get().flatMap { response =>
          if (response.status == 200) Future.successful(response.json.as[T])
          else Future.failed(new LdJamApiException(url, response.status, response.body))
        }
    }

    private def findPostNodeIdsForUser(userid: Int): Future[Seq[Int]] = {
        val key = CacheHelper.jamUserFeed(userid)
        cache.get[Seq[Int]](key) flatMap {
            case Some(nodes) => Future.successful(nodes)
            case _ =>
                getNodeFeed(userid, Seq("author"), "post", None, None, 0, 50) flatMap { response =>
                    val nodes = response.feed.map(_.id)
                    cache.set(key, nodes, CacheDuration).map(_ => nodes)
                } recover { case _: LdJamApiException => Nil }
        }
    }

    private def loadNodes(ids: Seq[Int]): Future[Seq[Node]] = {
        if (ids.isEmpty) Future.successful(Nil)
        else {
            val cachedNodes = Future.sequence(ids.map(i => cache.get[Node](CacheHelper.jamNode(i)))).map(_.flatten)

            cachedNodes.flatMap { knownNodes =>
                val knownIds = knownNodes.map(_.id).toSet
                val leftOver = ids.filterNot(knownIds.contains)
                if (leftOver.isEmpty) Future.successful(knownNodes)
                else {
                    val urlSection = leftOver.mkString("+")
                    logger.info(s"Cache missed for $urlSection, loading...")
                    getNodes(leftOver) flatMap { response =>
                        val newNodes =  response.node
                        Future.sequence(newNodes.map(n => cache.set(CacheHelper.jamNode(n.id), n, CacheDuration)))
                          .map(_ => newNodes)
                    }
                }
            }
        }
    }

    private def nodesToPosts(nodes: Seq[Node]) = {
        if (nodes.isEmpty) Nil
        else {
            val (users, posts) = nodes.foldLeft((Seq.empty[UserNode], Seq.empty[PostNode])) { case (existing @ (users, posts), node) =>
                node match {
                    case u: UserNode => (users :+ u, posts)
                    case p: PostNode => (users, posts :+ p)
                    case n =>
                        logger.warn(s"Unknown node: $n")
                        existing
                }
            }
            val authors = users.map(n => n.id -> n).toMap

            posts.map { n =>
                val author = authors.getOrElse(n.author, {
                    logger.warn(s"Unknown user ID found in post ${n.id}: ${n.author}. Known authors: ${users.map(_.id).mkString(",")}")
                    UserNode(n.author, -1, n.author, "author", FuzzyNone, FuzzyNone, Instant.now(), Instant.now(), Instant.now(), -1, "unknown", "Unknown", "", "", Nil, -1, FuzzyNone, 0, 0)
                })
                val avatar = author.meta.flatMap(_.avatar).map(avatar => cdnBaseUrl + avatar.substring(2) + ".32x32.fit.jpg")
                val body = compileMarkdown(n.body)
                LdjamPost(n.id, n.name, author, fixupNodeBody(body), n.created.atZone(ZoneId.systemDefault()), s"$pageBaseUrl${author.path}", avatar)
            }
        }
    }

    private def nodeToJamEntry(node: Node) = {
        // TODO get authors \ "link" \ "author" - list
        // TODO cleanup body
        LdJamEntry(node.id, node.name, fixupNodeBody(node.body))
    }

    private def fixupNodeBody(rawBody: String): String = {
        val r = "//(/raw/[^.].\\w+)".r
        val r2 = "<p>(<img[^>]+>)</p>".r
        var body = r.replaceAllIn(rawBody, cdnBaseUrl + _.group(1))
        body = compileMarkdown(body)
        body = r2.replaceAllIn(body, "<div class=\"image-container\">" + _.group(1) + "</div>")
        body
    }

    def getPost(id: Int): Future[Option[LdjamPost]] = {
        getPosts(_ => Seq(id)).map(_.headOption)
    }

    def getPosts(amount: Int): Future[Seq[LdjamPost]] = {

        getPosts(_.take(amount))
    }

    def allPosts: Future[Seq[LdjamPost]] = {
        getPosts(ids => ids)
    }

    private def getPosts(selector: Seq[Int] => Seq[Int]): Future[Seq[LdjamPost]] = {

        def select(nodes: Seq[Seq[Int]]) = {
            val selection = selector(nodes.flatten.sortBy(-_))
            if (selection.isEmpty) Nil
            else selection ++ accountIds
        }

        Future.sequence(accountIds.map(findPostNodeIdsForUser))
            .map(select)
            .flatMap(loadNodes)
            .map(nodesToPosts)
    }

    def findEntry(project: Project): Future[Option[LdJamEntry]] = {
        project.jam match {
            case Some(jam) =>
                val cacheKey = CacheHelper.jamEntry(project.repoName)

                cache.getOrElseUpdate[Int](cacheKey) {
                    logger.info(s"Cache missed for $cacheKey, loading...")
                    walk(1, jam.fixedSite().getPath).map { response =>
                        cache.set(cacheKey, response.node, CacheDuration)
                        response.node
                    }
                }.map(Some.apply).recover(_ => None).flatMap {
                    case Some(node) =>
                        loadNodes(Seq(node)).map( a => a.headOption.map(nodeToJamEntry))
                    case None =>
                        Future.successful(None)
                }
            case None => Future.successful(None)
        }
    }

    def urlList(values: Seq[?]): String = values.map(String.valueOf).mkString("+")

    def getNodeFeed(node: Int, methods: Seq[String], mainType: String, subType: Option[String], subSubType: Option[String], offset: Int, limit: Int): Future[NodeFeedResponse] = {
        if (limit > MaxLimit) {
            throw new IllegalArgumentException("limit must be <= 50")
        }
        val t = (Seq(mainType) ++ subType ++ subSubType).mkString("/")

        val url = s"$apiBaseUrl/vx/node/feed/$node/${urlList(methods)}/$t?offset=$offset&limit=$limit"
        get[NodeFeedResponse](url)
    }

    def getFeedOfNodes(node: Int, methods: Seq[String], mainType: String, subType: Option[String], subSubType: Option[String], sliceSize: Int = MaxLimit): Future[Seq[Node]] = {

        def getSlide(offset: Int, nodes: Seq[Node]): Future[Seq[Node]] = {
            getNodeFeed(node, methods, mainType, subType, subSubType, offset, sliceSize).flatMap { response =>
                if (response.feed.isEmpty) Future.successful(nodes)
                else {
                    getNodes2(response.feed.map(_.id)).flatMap { nodesResponse =>
                        getSlide(offset + sliceSize, nodes ++ nodesResponse.node)
                    }
                }
            }
        }

        getSlide(0, Vector.empty)
    }

    def getNodes(nodes: Seq[Int]): Future[NodeGetResponse] = {
        val url = s"$apiBaseUrl/vx/node/get/${urlList(nodes)}"
        get[NodeGetResponse](url)
    }

    def getNodes2(nodes: Seq[Int]): Future[Node2GetResponse] = {
        val url = s"$apiBaseUrl/vx/node2/get/${urlList(nodes)}"
        get[Node2GetResponse](url)
    }

    def walk(root: Int, path: String): Future[NodeWalkResponse] = {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must begin with a /")
        }

        val url = s"$apiBaseUrl/vx/node/walk/$root$path"
        get[NodeWalkResponse](url)
    }

    def walk2(root: Int, path: String): Future[Node2WalkResponse] = {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must begin with a /")
        }

        val url = s"$apiBaseUrl/vx/node2/walk/$root$path"
        get[Node2WalkResponse](url)
    }
}

case class LdjamPost(id: Int, name: String, author: UserNode, body: String, createdAt: ZonedDateTime, authorLink: String, avatarLink: Option[String]) extends BlogPost {
    override def anchor: String = s"ldjam_$id"

    override def truncatedBody(paragraphs: Int): String = body
}

case class LdJamEntry(id: Int, name: String, body: String)

case class LdjamEvent(id: Int, name: String, body: String)

class LdJamApiException(url: String, status: Int, body: String) extends RuntimeException(s"$url -> $status -> $body")

sealed trait ApiResponse {
    val status: Int
    val caller_id: Int
}

final case class NodeGetResponse(status: Int, caller_id: Int, cached: Option[Seq[Int]], node: Seq[Node]) extends ApiResponse
object NodeGetResponse {
    implicit val format: Format[NodeGetResponse] = Json.format
}

final case class Node2GetResponse(status: Int, caller_id: Int, cached: Option[Seq[Int]], node: Seq[Node]) extends ApiResponse
object Node2GetResponse {
    implicit val format: Format[Node2GetResponse] = Json.format
}

final case class NodeFeedResponse(status: Int, caller_id: Int, method: Seq[String], types: Seq[String], subtypes: Seq[String], offset: Int, limit: Int, feed: Seq[NodeFeedEntry], cached: Option[Boolean]) extends ApiResponse
object NodeFeedResponse {
    implicit val format: Format[NodeFeedResponse] = Json.format
}
case class NodeFeedEntry(id: Int, modified: Instant)
object NodeFeedEntry {
    implicit val format: Format[NodeFeedEntry] = Json.format
}
final case class NodeWalkResponse(status: Int, caller_id: Int, root: Int, path: Seq[Int], node: Int) extends ApiResponse
object NodeWalkResponse {
    implicit val format: Format[NodeWalkResponse] = Json.format
}

final case class Node2WalkResponse(status: Int, caller_id: Int, root: Int, path: Seq[Int], node_id: Int, nodes_cached: Seq[Int], node: Seq[Node]) extends ApiResponse
object Node2WalkResponse {
    implicit val format: Format[Node2WalkResponse] = Json.format
}

sealed trait Node {
    val id: Int
    val parent: Int
    val author: Int
    val `type`: String
    val subtype: FuzzyOption[String]
    val subsubtype: FuzzyOption[String]
    val published: Instant
    val created: Instant
    val modified: Instant
    val version: Int
    val slug: String
    val name: String
    val body: String
    val path: String
    val parents: Seq[Int]
    val love: Int
}
object Node {
    implicit val format: Format[Node] = new Format[Node] {
        override def writes(o: Node): JsValue = o match {
            case e: EventNode => implicitly[Writes[EventNode]].writes(e)
            case a: UserNode => implicitly[Writes[UserNode]].writes(a)
            case p: PostNode => implicitly[Writes[PostNode]].writes(p)
            case g: GameNode => implicitly[Writes[GameNode]].writes(g)
            case x: GenericNode => implicitly[Writes[GenericNode]].writes(x)
        }

        override def reads(json: JsValue): JsResult[Node] = (json \ "type", json \ "subtype", json \ "subsubtype") match {
            case (JsDefined(JsString("event")), _, _) => EventNode.format.reads(json)
            case (JsDefined(JsString("user")), _, _) => UserNode.format.reads(json)
            case (JsDefined(JsString("post")), _, _) => PostNode.format.reads(json)
            case (JsDefined(JsString("item")), JsDefined(JsString("game")), _) => GameNode.format.reads(json)
            case _ => GenericNode.format.reads(json)
        }
    }
}

sealed trait FuzzyOption[+A] {
    def get: A
    def getOrElse[B >: A](default: => B): B
    def toOption: Option[A]
    def filter(f: A => Boolean): Option[A] = toOption.filter(f)
    def map[B](f: A => B): Option[B] = toOption.map(f)
    def flatMap[B](f: A => Option[B]): Option[B] = toOption.flatMap(f)
}

object FuzzyOption {
    def apply[A](value: A): FuzzyOption[A] = if (value == null) FuzzyNone else FuzzySome(value)
    def apply[A](opt: Option[A]): FuzzyOption[A] = opt match {
        case Some(value) => FuzzySome(value)
        case None => FuzzyNone
    }

    implicit def fuzzyOptionReads[T](implicit fmt: Reads[T]): Reads[FuzzyOption[T]] = {
        case JsNull => JsSuccess(FuzzyNone)
        case JsString("") => JsSuccess(FuzzyNone)
        case JsArray(elems) if elems.isEmpty => JsSuccess(FuzzyNone)
        case JsObject(elems) if elems.isEmpty => JsSuccess(FuzzyNone)
        case json => fmt.reads(json).map(FuzzySome.apply)
    }

    implicit def fuzzyOptionWrites[T](implicit fmt: Writes[T]): Writes[FuzzyOption[T]] = {
        case FuzzyNone => JsNull
        case FuzzySome(value) => fmt.writes(value)
    }
}

final case class FuzzySome[+A](value: A) extends FuzzyOption[A] {
    override def get: A = value
    override def getOrElse[B >: A](default: => B): B = value
    override def toOption: Option[A] = Some(value)
}
case object FuzzyNone extends FuzzyOption[Nothing] {
    override def get: Nothing = throw new NoSuchElementException("FuzzyNone.get")
    override def getOrElse[B >: Nothing](default: => B): B = default
    override def toOption: Option[Nothing] = None
}

final case class GenericNode(id: Int, parent: Int, author: Int, `type`: String, subtype: FuzzyOption[String], subsubtype: FuzzyOption[String], published: Instant, created: Instant, modified: Instant, version: Int, slug: String, name: String, body: String, path: String, parents: Seq[Int], meta: FuzzyOption[Map[String, String]], love: Int) extends Node
object GenericNode {
    implicit val format: Format[GenericNode] = Json.format
}

final case class UserNode(id: Int, parent: Int, author: Int, `type`: String, subtype: FuzzyOption[String], subsubtype: FuzzyOption[String], published: Instant, created: Instant, modified: Instant, version: Int, slug: String, name: String, body: String, path: String, parents: Seq[Int], love: Int, meta: FuzzyOption[UserMetadata], games: Int, posts: Int) extends Node
case class UserMetadata(avatar: Option[String])
object UserNode {
    implicit val format: Format[UserNode] = Json.format
}
object UserMetadata {
    implicit val format: Format[UserMetadata] = Json.format
}

final case class EventNode(id: Int, parent: Int, author: Int, `type`: String, subtype: FuzzyOption[String], subsubtype: FuzzyOption[String], published: Instant, created: Instant, modified: Instant, version: Int, slug: String, name: String, body: String, path: String, parents: Seq[Int], love: Int) extends Node
object EventNode {
    implicit val format: Format[EventNode] = Json.format
}

final case class PostNode(id: Int, parent: Int, author: Int, `type`: String, subtype: FuzzyOption[String], subsubtype: FuzzyOption[String], published: Instant, created: Instant, modified: Instant, version: Int, slug: String, name: String, body: String, path: String, parents: Seq[Int], love: Int) extends Node
object PostNode {
    implicit val format: Format[PostNode] = Json.format
}

final case class GameNode(id: Int, parent: Int, author: Int, `type`: String, subtype: FuzzyOption[String], subsubtype: FuzzyOption[String], published: Instant, created: Instant, modified: Instant, version: Int, slug: String, name: String, body: String, path: String, parents: Seq[Int], love: Int, meta: GameMetadata, grades: Option[Map[String, Int]], notes: Option[Int], `notes-timestamp`: Option[Instant], magic: GameMagic) extends Node
object GameNode {
    implicit val format: Format[GameNode] = Json.format
}

case class GameMetadata(author: Seq[Int], `allow-anonymous-comments`: Option[JsValue], cover: Option[String], `link-01`: Option[String], `link-01-tag`: Option[JsValue])
object GameMetadata {
    implicit val format: Format[GameMetadata] = Json.format
}

case class GameMagic(cool: Double, feedback: Int, `given`: Double, grade: Double, smart: Double)
object GameMagic {
    implicit val format: Format[GameMagic] = Json.format
}
