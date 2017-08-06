package service

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util
import javax.inject.Inject

import com.tumblr.jumblr.JumblrClient
import com.tumblr.jumblr.types.{Post, TextPost}
import play.api.cache.AsyncCacheApi
import play.api.{Configuration, Logger}
import service.CacheHelper.BlogCacheKey

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case class TumblrPost(id: Long, createdAt: ZonedDateTime, title: String, body: String, tags: Seq[String], blogName: String) extends BlogPost {
    override def anchor: String = s"tumblr_$id"

    private val paragraphSeparator = "</p>"

    lazy val paragraphs: Seq[String] = body.split(paragraphSeparator)

    override def truncatedBody(firstN: Int): String = {
        if (this.paragraphs.length < firstN) {
            this.paragraphs.take(firstN).mkString(paragraphSeparator) + paragraphSeparator
        } else {
            body
        }
    }
}

class TumblrService @Inject()(conf: Configuration, cache: AsyncCacheApi, implicit val ec: ExecutionContext) {
    private val tumblrDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    private val client =
        new JumblrClient(conf.get[String]("tumblr.customerkey"), conf.get[String]("tumblr.customersecret"))

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

    def convertPost(post: Post): TumblrPost = {
        val date = ZonedDateTime.parse(post.getDateGMT, tumblrDateFormat)
        post match {
            case p: TextPost =>
                TumblrPost(p.getId, date, p.getTitle, p.getBody, p.getTags.asScala, p.getBlogName)
            case p =>
                Logger.warn(s"Incompatible Tumblr post type: ${p.getClass.getName}")
                TumblrPost(p.getId, date, p.getSourceTitle, "Can't handle this", p.getTags.asScala, p.getBlogName)
        }
    }

    def allPosts: Future[Seq[TumblrPost]] = {
        val blogName = "bananafourlife"
        val pageSize = 20

        cache.getOrElseUpdate(BlogCacheKey, Duration.Inf) {
            Future {
                val postCount = client.blogInfo(blogName).getPostCount
                val pageCount = math.ceil(postCount / pageSize.toFloat).toInt

                Logger.info(s"Tumblr has $postCount posts resulting in $pageCount requests!")

                val options = new util.HashMap[String, Int]()
                options.put("limit", pageSize)

                (0 until pageCount).map(_ * pageSize).flatMap { offset =>
                    options.put("offset", offset)
                    client.blogPosts(blogName, options).asScala.map(convertPost)
                }
            }
        }
    }
}
