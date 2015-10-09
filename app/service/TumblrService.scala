package service

import java.util
import javax.inject.Inject

import com.tumblr.jumblr.JumblrClient
import com.tumblr.jumblr.types.Post
import play.api.Application
import play.api.cache.CacheApi
import play.twirl.api.TemplateMagic.javaCollectionToScala

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TumblrService @Inject() (app: Application, cache: CacheApi) {
  val maxPosts = 5
  val client = {
    for {
      key <- app.configuration.getString("tumblr.customerkey")
      secret <- app.configuration.getString("tumblr.customersecret")
    } yield new JumblrClient(key, secret)
  }
  var postCountLast = 0

  def getPosts(page: Int): Future[List[Post]] = Future {

    val client = this.client.get

    val postCount = cache.getOrElse("blog.count", 2.hours) {
      client.blogInfo("bananafourlife").getPostCount
    }

    if (postCount > postCountLast) {
      postCountLast = postCount
      while (postCountLast > 0)
      {
        cache.remove("blog.page." + postCountLast / maxPosts)
        postCountLast -= maxPosts
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
