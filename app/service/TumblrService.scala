package service

import java.util
import javax.inject.Inject

import com.tumblr.jumblr.JumblrClient
import com.tumblr.jumblr.types.Post
import play.api.Configuration
import play.api.cache.CacheApi
import play.twirl.api.TemplateMagic.javaCollectionToScala
import service.CacheHelper.{BlogCacheKey, CacheDuration}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TumblrService @Inject()(config: Configuration, cache: CacheApi) {
    val maxPosts = 5
    val client = {
        for {
            key <- config.getString("tumblr.customerkey")
            secret <- config.getString("tumblr.customersecret")
        } yield new JumblrClient(key, secret)
    }
    var postCount = 0

    def getPost(id: Long): Future[Option[Post]] = {
        getPosts.map(list => list.find(post => id == post.getId))
    }

    def getPosts(page: Int): Future[List[Post]] = {
        getPosts.map(list => list.slice(page * maxPosts, page * maxPosts + maxPosts))
    }

    def getPosts: Future[List[Post]] = Future {
        val client = this.client.get
        val blogName = "bananafourlife"

        postCount = cache.getOrElse(CacheHelper.BlogCountCacheKey, CacheDuration) {
            client.blogInfo(blogName).getPostCount
        }

        cache.getOrElse(BlogCacheKey, CacheDuration) {
            (0 to postCount / 20).toList.flatMap(i => {
                val options = new util.HashMap[String, Int]()
                options.put("limit", 20)
                options.put("offset", i * 20)
                client.blogPosts(blogName, options).toList
            })
        }
    }
}
