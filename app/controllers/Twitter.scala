package controllers

import play.twirl.api.Html
import play.twirl.api.HtmlFormat._
import play.twirl.api.TemplateMagic.javaCollectionToScala
import twitter4j.{Status, TwitterFactory}

trait Twitter {

  val twitter = TwitterFactory.getSingleton

  def compileTweet(tweet: Status): Html = {
    val hashtags = tweet.getHashtagEntities.map(h  => (h.getStart, h.getEnd, ("#" + h.getText, "#" + h.getText)))
    val urls = tweet.getURLEntities.map(u => (u.getStart, u.getEnd, (u.getExpandedURL, u.getDisplayURL)))
    val users = tweet.getUserMentionEntities.map(u => (u.getStart, u.getEnd, ("#" + u.getName, "@" + u.getScreenName)))

    val elements = (hashtags ++ urls ++ users).sortWith((a, b) => a._1 < b._1)

    val text = tweet.getText
    val out = new StringBuilder
    var last = 0
    for ((start, end, (href, linkText)) <- elements) {
      out ++= text.substring(last, start)
      out ++= s"""<a href="${escape(href)}">${escape(linkText)}</a>"""
      last = end
    }
    out ++= text.substring(last)
    Html(out.toString())
  }

  def tweets(user: String) = twitter.getUserTimeline(user)
  
  def compiledTweets(user: String) = tweets(user).toList.map(compileTweet)
}