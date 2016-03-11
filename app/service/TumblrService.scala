package service

import java.util
import javax.inject.Inject

import com.tumblr.jumblr.JumblrClient
import com.tumblr.jumblr.types.Post
import play.api.{Configuration}
import play.api.cache.CacheApi
import play.twirl.api.TemplateMagic.javaCollectionToScala

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TumblrService @Inject() (config: Configuration, cache: CacheApi) {
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

   var postCount = cache.getOrElse("blog.count", 2.hours) {
      client.blogInfo("bananafourlife").getPostCount
    }

    if (postCount > postCountLast) {
      postCountLast = postCount
      while (postCount > 0)
      {
        cache.remove("blog.page." + postCount / maxPosts)
        postCount -= maxPosts
      }
    }

    cache.getOrElse("blog.page." + page) {
      val options = new util.HashMap[String, Int]()
      options.put("limit", maxPosts)
      options.put("offset", page * maxPosts)
      client.blogPosts("bananafourlife", options).toList
    }
  }
}
