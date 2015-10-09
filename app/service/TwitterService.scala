package service

import java.text.SimpleDateFormat
import javax.inject.Inject

import play.api.cache.CacheApi
import play.twirl.api.Html
import play.twirl.api.HtmlFormat._
import play.twirl.api.TemplateMagic.javaCollectionToScala
import twitter4j.{ResponseList, Status, TwitterFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TwitterService @Inject() (cache: CacheApi) {

  val twitter = TwitterFactory.getSingleton
  val dateFormat = new SimpleDateFormat("dd MMMMM yyyy")

  def compileTweet(tweet: Status): Html = {
    val user = tweet.getUser
    val userName = user.getName
    val userHandle = user.getScreenName
    val imgURL = user.getOriginalProfileImageURLHttps
    val tweetId = tweet.getId

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
    views.html.snippet.twitter(imgURL, tweetId, userName, userHandle, Html(out.toString()), dateFormat.format(tweet.getCreatedAt))
  }

  def tweets(user: String) = Future {
    cache.getOrElse("remote.twitter." + user, 2.hours) {
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
