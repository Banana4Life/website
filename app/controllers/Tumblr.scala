package controllers

import play.api.Play.{current => app}
import com.tumblr.jumblr.JumblrClient

trait Tumblr {
  val maxPosts = 5
  val client = new JumblrClient(app.configuration.getString("secret.customerkey").get, app.configuration.getString("secret.customersecret").get)

  def getPosts(page: Int) = ???
}
