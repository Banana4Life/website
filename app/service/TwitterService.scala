package service

import java.text.SimpleDateFormat
import java.util.Properties
import javax.inject.{Inject, Singleton}

import play.api.Mode.Dev
import play.api.{Configuration, Environment, Logger, Mode}
import play.api.cache.SyncCacheApi
import play.twirl.api.Html
import play.twirl.api.HtmlFormat._
import service.CacheHelper.{CacheDuration, TwitterCacheKeyPrefix}
import twitter4j.conf.PropertyConfiguration
import twitter4j.{Status, TwitterFactory}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TwitterService @Inject()(cache: SyncCacheApi, conf: Configuration, env: Environment, implicit val ec: ExecutionContext) {

    private val twitter = {
        val props = new Properties()
        props.put("debug", if (env.mode == Dev) "true" else "false")

        for (prop <- Seq("consumerKey", "consumerSecret", "accessToken", "accessTokenSecret")) {
            props.put(s"oauth.$prop", conf.get[String](s"twitter.$prop"))
        }

        new TwitterFactory(new PropertyConfiguration(props)).getInstance()
    }
    val dateFormat = new SimpleDateFormat("dd MMMMM yyyy")

    val TypePhoto = "photo"

    private val patternHashtag = "#(.+)".r
    private val patternAt = "@(.+)".r

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
                case patternHashtag(c) => s"https://twitter.com/hashtag/$c"
                case patternAt(c) => s"https://twitter.com/$c"
                case _ => href
            }

            out ++= s"""<a href="${escape(link)}">${escape(linkText)}</a>"""

            last = end
        }
        out ++= text.substring(last)

        // add images
        for (media <- tweet.getMediaEntities) {
            if (media.getType == TypePhoto) {
                out ++= s"""<div class="box"><img src="${escape(media.getMediaURLHttps)}" alt="Help me! I am trapped behind this image"></div>"""
            }
            // TODO handle other types of media
        }

        views.html.snippet.twitter(imgURL, tweetId, userName, userHandle, Html(out.toString()), dateFormat.format(tweet.getCreatedAt))
    }

    def tweets(user: String): Future[Seq[Status]] = {
        Future {
            cache.getOrElseUpdate(s"$TwitterCacheKeyPrefix." + user, CacheDuration) {
                try {
                    twitter.getUserTimeline(user).asScala
                } catch {
                    case e: Exception =>
                        Logger.error("Failed to get the tweets!", e)
                        Nil
                }
            }
        }
    }

    def compiledTweets(user: String): Future[Seq[Html]] = tweets(user) map {
        _.map(compileTweet)
    }

    def compiledTweets(user: String, count: Int): Future[Seq[Html]] = tweets(user) map {
        _.map(compileTweet).take(count)
    }
}
