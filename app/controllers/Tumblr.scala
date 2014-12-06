package controllers

import java.util

import com.tumblr.jumblr.JumblrClient
import play.api.Play.{current => app}
import play.api.cache.Cache

trait Tumblr {
  val maxPosts = 5
  val client = new JumblrClient(app.configuration.getString("secret.customerkey").get, app.configuration.getString("secret.customersecret").get)
  var postCountLast = 0

  def getPosts(page: Int) = {
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
      client.blogPosts("bananafourlife", options)
    }
  }
}
