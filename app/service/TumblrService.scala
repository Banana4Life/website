package service

import java.util
import javax.inject.Inject

import com.tumblr.jumblr.JumblrClient
import com.tumblr.jumblr.types.Post
import play.api.Configuration
import play.api.cache.SyncCacheApi
import service.CacheHelper.{BlogCacheKey, CacheDuration}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class TumblrService @Inject()(conf: Configuration, cache: SyncCacheApi, implicit val ec: ExecutionContext) {
    val maxPosts = 5
    private val client =
        new JumblrClient(conf.get[String]("tumblr.customerkey"), conf.get[String]("tumblr.customersecret"))
    var postCount = 0

    def getPost(id: Long): Future[Option[Post]] = {
        getPosts.map(list => list.find(post => id == post.getId))
    }

    def getPosts(page: Int): Future[Seq[Post]] = {
        getPosts.map(list => list.slice(page * maxPosts, page * maxPosts + maxPosts))
    }

    def getPosts: Future[Seq[Post]] = Future {
        val blogName = "bananafourlife"

        postCount = cache.getOrElseUpdate(CacheHelper.BlogCountCacheKey, CacheDuration) {
            client.blogInfo(blogName).getPostCount
        }

        cache.getOrElseUpdate(BlogCacheKey, CacheDuration) {
            (0 to postCount / 20).flatMap(i => {
                val options = new util.HashMap[String, Int]()
                options.put("limit", 20)
                options.put("offset", i * 20)
                client.blogPosts(blogName, options).asScala
            })
        }
    }
}
