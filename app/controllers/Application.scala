package controllers

import javax.inject.Inject

import com.tumblr.jumblr.types.{TextPost, Post}
import play.api.cache.Cached
import play.api.mvc._
import play.twirl.api.Html
import service._

import scala.concurrent.ExecutionContext.Implicits.global

class Application @Inject() (cached: Cached,
                              github: GithubService,
                              tumblr: TumblrService,
                              twitter: TwitterService,
                              youtube: YoutubeService,
                              twitch: TwitchService) extends Controller {

    def index = Action.async {
        for {
            tweets <- twitter.compiledTweets("bananafourlife", 4)
            posts <- tumblr.getPosts(0)
            projects <- github.getProjects
            twitchPlayer <- twitch.getPlayer
        } yield {
            val postsHtml = posts.map(p => Html(p.asInstanceOf[TextPost].getTitle))
            val projectsHtml = views.html.projects(projects)
            Ok(views.html.index(tweets, postsHtml, projectsHtml, twitchPlayer))
        }
    }

    def blog(page: Int) = Action.async {
        tumblr.getPosts(page) map {
            posts: List[Post] => Ok(views.html.blog(posts, page > 0, tumblr.postCountLast / tumblr.maxPosts > page + 1, page))
        }
    }

    def snippets = Action.async {
        twitter.compiledTweets("bananafourlife") map {
            statuses: List[Html] => Ok(views.html.snippets(statuses))
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
}


