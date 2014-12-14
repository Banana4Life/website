package service

import java.util

import com.tumblr.jumblr.JumblrClient
import com.tumblr.jumblr.types.Post
import play.api.Play.{current => app}
import play.api.cache.Cache
import play.twirl.api.TemplateMagic.javaCollectionToScala

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Tumblr {
  val maxPosts = 5
  val client = {
    for (
      key <- app.configuration.getString("tumblr.customerkey");
      secret <- app.configuration.getString("tumblr.customersecret")
    ) yield new JumblrClient(key, secret)
  }
  var postCountLast = 0

  def getPosts(page: Int): Future[List[Post]] = Future {

    val client = this.client.get

    val postCount = Cache.getOrElse("blog.count", 60 * 60 * 2 /*2h*/) {
      client.blogInfo("bananafourlife").getPostCount
    }

    if (postCount > postCountLast) {
      postCountLast = postCount
      while (postCountLast > 0)
      {
        Cache.remove("blog.page." + postCountLast / maxPosts)
        postCountLast -= maxPosts
      }
    }

    Cache.getOrElse("blog.page." + page) {
      val options = new util.HashMap[String, Int]()
      options.put("limit", maxPosts)
      options.put("offset", page * maxPosts)
      client.blogPosts("bananafourlife", options).toList
    }
  }
}
