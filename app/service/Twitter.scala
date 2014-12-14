package service

import java.text.SimpleDateFormat

import play.api.Play.current
import play.api.cache.Cache
import play.twirl.api.Html
import play.twirl.api.HtmlFormat._
import play.twirl.api.TemplateMagic.javaCollectionToScala
import twitter4j.{ResponseList, Status, TwitterFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Twitter {

  val twitter = TwitterFactory.getSingleton
  val dateFormat = new SimpleDateFormat("MM-dd-yyyy")

  def compileTweet(tweet: Status): Html = {
    val hashtags = tweet.getHashtagEntities.map(h => (h.getStart, h.getEnd, ("#" + h.getText, "#" + h.getText)))
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
    views.html.snippet.twitter(Html(out.toString()), dateFormat.format(tweet.getCreatedAt))
  }

  def tweets(user: String) = Future {
    Cache.getOrElse("remote.twitter." + user, 60 * 60 * 2) {
      twitter.getUserTimeline(user)
    }
  }

  def compiledTweets(user: String) = tweets(user) map {
    statuses: ResponseList[Status] => statuses.toList.map(compileTweet)
  }

  def compiledTweets(user: String, count: Int) = tweets(user) map {
    statuses: ResponseList[Status] => statuses.toList.map(compileTweet).take(count)
  }
}
