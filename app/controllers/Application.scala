package controllers

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import com.tumblr.jumblr.types.{Post, TextPost}
import play.api.cache.Cached
import play.api.mvc._
import service._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Application @Inject() (cached: Cached,
                             github: GithubService,
                             tumblr: TumblrService,
                             twitter: TwitterService,
                             youtube: YoutubeService,
                             twitch: TwitchService,
                             searchIndex: SearchIndex,
                             components: ControllerComponents) extends AbstractController(components) {

    def index = Action.async {
        for {
            projects <- github.getProjects
            posts <- tumblr.getPosts(0)
            tweets <- twitter.compiledTweets("bananafourlife", 9)
            videos <- youtube.getVideos
            twitchPlayer <- twitch.getPlayer
        } yield {
            val projectsHtml = projects.map(project => (LocalDate.parse(project.createdAt.toString, DateTimeFormatter.ofPattern("E MMM dd HH:mm:ss z yyyy")), views.html.snippet.project(project)))
            val postsHtml = posts.map(post => (LocalDate.parse(post.getDateGMT, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")), views.html.snippet.blogpost(post, 0, trunc = true)))
            val videosHtml = videos.map(video => (LocalDate.parse(video.publishedAt.toString, DateTimeFormatter.ofPattern("E MMM dd HH:mm:ss z yyyy")), views.html.snippet.youtube(video, twitter.dateFormat.format(video.publishedAt))))
            val activities = (postsHtml ++ projectsHtml ++ videosHtml).sortWith((a, b) => a._1.isAfter(b._1)).map(_._2).take(5)

            Ok(views.html.index(tweets, activities, twitchPlayer))
        }
    }

    def blog(page: Int) = Action.async {
        tumblr.getPosts(page) map {
            posts: Seq[Post] => Ok(views.html.blog(posts.map(post => views.html.snippet.blogpost(post, page, trunc = false)), page > 0, tumblr.postCount.toFloat / tumblr.maxPosts > page + 1, page))
        }
    }

    def projects = cached((x: RequestHeader) => "page.projects", 60 * 60 * 2) {
        Action.async {
            github.getProjects map {projects =>
                Ok(views.html.projects(projects))
            }
        }
    }

    def about = Action {
        Ok(views.html.about())
    }

    def search(query: String) = Action.async {
        tumblr.getPosts.map(posts => Ok(views.html.blog(
            searchIndex.query(posts, query).map(doc => views.html.snippet.blogpost(doc.asInstanceOf[TumblrDoc].post, 0, trunc = false)), prev = false, next = false, 0
        )))
    }
}


