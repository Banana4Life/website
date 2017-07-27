package controllers

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import com.tumblr.jumblr.types.Post
import play.api.cache.Cached
import play.api.mvc._
import service._

import scala.concurrent.ExecutionContext.Implicits.global

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
            val projectsHtml = projects.map(project => (project.createdAt, views.html.snippet.project(project)))
            val postsHtml = posts.map(post => (ZonedDateTime.parse(post.getDateGMT, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")), views.html.snippet.blogpost(post, 0, trunc = true)))
            val videosHtml = videos.map(video => (video.publishedAt, views.html.snippet.youtube(video, video.publishedAt.format(DateTimeFormatter.ofPattern("dd MMMMM yyyy")))))
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


