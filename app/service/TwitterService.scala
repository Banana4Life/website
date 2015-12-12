package service

import java.text.SimpleDateFormat
import javax.inject.Inject

import play.api.Logger
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

  val TypePhoto = "photo"

  val patternHashtag = "#(.+)".r
  val patternAt = "@(.+)".r

  def compileTweet(tweet: Status): Html = {
    val user = tweet.getUser
    val userName = user.getName
    val userHandle = user.getScreenName
    val imgURL = user.getOriginalProfileImageURLHttps
    val tweetId = tweet.getId

    val hashtags = tweet.getHashtagEntities.map(h => (h.getStart, h.getEnd, ("#" + h.getText, "#" + h.getText)))
    val urls = tweet.getURLEntities.map(u => (u.getStart, u.getEnd, (u.getExpandedURL, u.getDisplayURL)))
    val users = tweet.getUserMentionEntities.map(u => (u.getStart, u.getEnd, (u.getName, "@" + u.getScreenName)))

    val elements = (hashtags ++ urls ++ users).sortWith((a, b) => a._1 < b._1)

    val text = tweet.getText
    val out = new StringBuilder
    var last = 0
    for ((start, end, (href, linkText)) <- elements) {
      out ++= text.substring(last, start)

      // get correct url for hashtags and usernames
      val link = linkText match {
        case patternHashtag(c) => "https://twitter.com/hashtag/" + c
        case patternAt(c) => "https://twitter.com/" + c
        case _ => href
      }

      out ++= s"""<a href="${escape(link)}">${escape(linkText)}</a>"""

      last = end
    }
    out ++= text.substring(last)

    // add images
    for (media <- tweet.getMediaEntities) {
      if (media.getType == TypePhoto) {
        out ++= s"""<div class="box"><img src="${escape(media.getMediaURLHttps)}" alt="Help me! I am trapped behind this image"></img></div>"""
      }
      // TODO handle other types of media
    }

    views.html.snippet.twitter(imgURL, tweetId, userName, userHandle, Html(out.toString()), dateFormat.format(tweet.getCreatedAt))
  }

  def tweets(user: String) = {
    val future = Future {
      cache.getOrElse("remote.twitter." + user, 2.hours) {
        twitter.getUserTimeline(user)
      }
    }
    future onFailure {
      case e: Exception =>
        Logger.error("Failed to get the tweets!", e)
    }
    future
  }

  def compiledTweets(user: String) = tweets(user) map {
    statuses: ResponseList[Status] => statuses.toList.map(compileTweet)
  }

  def compiledTweets(user: String, count: Int) = tweets(user) map {
    statuses: ResponseList[Status] => statuses.toList.map(compileTweet).take(count)
  }
}
