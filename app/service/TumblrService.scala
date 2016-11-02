package service

import java.util
import javax.inject.Inject

import com.tumblr.jumblr.JumblrClient
import com.tumblr.jumblr.types.Post
import play.api.Configuration
import play.api.cache.CacheApi
import play.twirl.api.TemplateMagic.javaCollectionToScala
import service.CacheHelper.{BlogCacheKeyPrefix, CacheDuration}

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
    var postCountLast = 0

    def getPosts(page: Int): Future[List[Post]] = Future {

        val client = this.client.get
        val blogName = "bananafourlife"

        var postCount = cache.getOrElse(CacheHelper.BlogCountCacheKey, CacheDuration) {
            client.blogInfo(blogName).getPostCount
        }

        if (postCount > postCountLast) {
            postCountLast = postCount
            while (postCount > 0) {
                cache.remove("blog.page." + postCount / maxPosts)
                postCount -= maxPosts
            }
        }

        cache.getOrElse(s"$BlogCacheKeyPrefix.$page", CacheDuration) {
            val options = new util.HashMap[String, Int]()
            options.put("limit", maxPosts)
            options.put("offset", page * maxPosts)
            client.blogPosts(blogName, options).toList
        }
    }
}
