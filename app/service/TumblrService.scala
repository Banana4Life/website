package service

import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.util
import javax.inject.Inject

import com.tumblr.jumblr.JumblrClient
import com.tumblr.jumblr.types.{Post, TextPost}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import service.CacheHelper.{BlogCacheKey, CacheDuration}

import scala.collection.JavaConverters._
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

class TumblrService @Inject()(conf: Configuration, cache: SyncCacheApi, implicit val ec: ExecutionContext) {
    private val tumblrDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    val maxPosts = 5
    private val client =
        new JumblrClient(conf.get[String]("tumblr.customerkey"), conf.get[String]("tumblr.customersecret"))
    var postCount = 0

    def getPost(id: Long): Future[Option[TumblrPost]] = {
        getPosts.map(list => list.find(post => id == post.id))
    }

    def getPosts(page: Int): Future[Seq[TumblrPost]] = {
        getPosts.map(list => list.slice(page * maxPosts, page * maxPosts + maxPosts))
    }

    def convertPost(post: Post): TumblrPost = {
        val date = ZonedDateTime.parse(post.getDateGMT, tumblrDateFormat)
        post match {
            case p: TextPost =>
                TumblrPost(p.getId, date, p.getTitle, p.getBody, p.getTags.asScala, p.getBlogName)
            case p: Post =>
                TumblrPost(p.getId, date, p.getSourceTitle, "Can't handle this", p.getTags.asScala, p.getBlogName)
        }
    }

    def getPosts: Future[Seq[TumblrPost]] = Future {
        val blogName = "bananafourlife"

        postCount = cache.getOrElseUpdate(CacheHelper.BlogCountCacheKey, CacheDuration) {
            client.blogInfo(blogName).getPostCount
        }

        cache.getOrElseUpdate(BlogCacheKey, CacheDuration) {
            (0 to postCount / 20).flatMap(i => {
                val options = new util.HashMap[String, Int]()
                options.put("limit", 20)
                options.put("offset", i * 20)
                client.blogPosts(blogName, options).asScala.map(convertPost)
            })
        }
    }
}
