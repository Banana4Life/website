package controllers

import javax.inject.Inject

import play.api.mvc._
import play.twirl.api.Html
import service.{ TwitterService, YoutubeService}
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Snippets @Inject() (youtubeService: YoutubeService, twitterService: TwitterService) extends Controller {

  def show = Action.async {
    type Snippet = (Long, () => Html)

    val tweets = twitterService.tweets("bananafourlife") map {tweets =>
      for (tweet <- tweets.asScala) yield {
        (tweet.getCreatedAt.getTime, () => twitterService.compileTweet(tweet))
      }
    }

    val videos = youtubeService.getVideos map {videos =>
      for (video <- videos) yield {
        (video.publishedAt.getValue, () => views.html.snippet.youtube(video))
      }
    }

    Future.sequence(Seq(tweets, videos))
      .map(_.foldLeft(Seq[Snippet]())((a, b) => a ++ b))
      .map(s => s.sortBy(-_._1))
      .map(s => s.map(s => s._2()))
      .map(s => Ok(views.html.snippets(s)))
  }

}
