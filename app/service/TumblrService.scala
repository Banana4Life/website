package service

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import io.circe.parser.parse
import io.circe.{Decoder, Json}
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.{BodyReadable, WSClient}
import play.api.{Configuration, Logger}
import service.CacheHelper.BlogCacheKey

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.*
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case class TumblrPost(id: Long, createdAt: ZonedDateTime, title: String, body: String, tags: Seq[String], blogName: String) extends BlogPost {
    override def anchor: String = s"tumblr_$id"

    private val paragraphSeparator = "</p>"

    lazy val paragraphs: Seq[String] = ArraySeq.unsafeWrapArray(body.split(paragraphSeparator))

    override def truncatedBody(firstN: Int): String = {
        if (this.paragraphs.length < firstN) {
            this.paragraphs.take(firstN).mkString(paragraphSeparator) + paragraphSeparator
        } else {
            body
        }
    }
}

private implicit def bodyReadable[T: Decoder]: BodyReadable[T] = BodyReadable[T] { response =>
    parse(response.bodyAsBytes.utf8String) match
        case Left(value) => throw value
        case Right(value) => value.as[T] match
            case Left(value) => throw value
            case Right(value) => value
}

private case class TumblrResponse[T](response: T) derives Decoder
private case class TumblrBlogResponse[T](blog: T) derives Decoder
private case class TumblrBlogInfo(posts: Int) derives Decoder
private case class TumblrBlogPosts(posts: Seq[TumblrBlogPost]) derives Decoder
private case class TumblrBlogPost(id: Long, date: String, title: String, body: String, tags: Vector[String], blog_name: String, source_title: Option[String]) derives Decoder

class TumblrService(conf: Configuration, ws: WSClient, cache: AsyncCacheApi, implicit val ec: ExecutionContext) {

    private val logger = Logger(classOf[TumblrService])

    private val tumblrDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    private val blogName = "bananafourlife"
    private val consumerKey = conf.get[String]("tumblr.consumerKey")
    private val tumblrApiBaseUrl = s"https://api.tumblr.com/v2/blog/$blogName.tumblr.com"

    def getPost(id: Long): Future[Option[TumblrPost]] = {
        allPosts.map(_.find(_.id == id))
    }

    def getPosts(amount: Int): Future[Seq[TumblrPost]] = {
        allPosts map { posts =>
            posts.take(amount)
        }
    }

    def getPosts(from: Long, limit: Int): Future[Seq[TumblrPost]] = {
        allPosts map { posts =>
            posts.dropWhile(_.id != from).take(limit)
        }
    }

    private def convertPost(post: TumblrBlogPost): TumblrPost = {
        val date = ZonedDateTime.parse(post.date, tumblrDateFormat)
        TumblrPost(post.id, date, post.title, post.body, post.tags, post.blog_name)
    }

    private def fetchAllPosts(pageSize: Int): Future[Seq[TumblrBlogPost]] = blogInfo() flatMap { info =>
        val postCount = info.posts
        val pageCount = math.ceil(postCount / pageSize.toFloat).toInt
        logger.info(s"Tumblr has $postCount posts resulting in $pageCount requests!")
        val pages = (0 until pageCount).map(_ * pageSize).map { offset =>
            blogPosts(offset, pageSize).map(posts => {
                println(posts)
                posts
            })
        }
        Future.sequence(pages).map(pages => {
            pages.flatten
        })
    }

    def allPosts: Future[Seq[TumblrPost]] = {
        cache.getOrElseUpdate(BlogCacheKey, Duration.Inf) {
            fetchAllPosts(pageSize = 20).map(_.map(convertPost))
        }.recover { t => {
            logger.error(s"Failed to fetch tumblr posts: ${t.getMessage}", t)
            Seq.empty
        }}
    }

    private def blogInfo(): Future[TumblrBlogInfo] = {
        queryTumblr[TumblrBlogResponse[TumblrBlogInfo]]("/info", Seq.empty).map(_.blog)
    }

    private def blogPosts(offset: Int, limit: Int): Future[Seq[TumblrBlogPost]] = {
        queryTumblr[TumblrBlogPosts]("/posts", Seq(("offset", offset.toString), ("limit", limit.toString))).map(_.posts)
    }

    private def queryTumblr[T: Decoder](path: String, queryParams: Seq[(String, String)]): Future[T] = {
        ws.url(s"$tumblrApiBaseUrl$path")
          .withQueryStringParameters(queryParams :+ (("api_key", consumerKey)) *)
          .get()
          .flatMap { res =>
            if 200 <= res.status && res.status < 300 then {
                Future.successful(res.body[TumblrResponse[T]])
            } else {
                Future.failed(IllegalStateException(s"Tumblr request failed: ${res.status}: ${res.bodyAsBytes.utf8String}"))
            }
          }
          .map(_.response)
    }
}
