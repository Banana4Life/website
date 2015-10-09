package controllers

import com.tumblr.jumblr.types.Post
import play.api.Play.current
import play.api.cache.Cached
import play.api.libs.json._
import play.api.libs.ws._
import play.api.mvc._
import play.twirl.api.Html
import service._
import service.github.Project

import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller with Twitter with Tumblr with Youtube with Github {

    def index = Action.async {
        for {
            tweets <- compiledTweets("bananafourlife", 4)
            posts <- getPosts(0)
            projects <- getProjects()
        } yield {
            val postsHtml = posts.map(p => Html(p.getSourceTitle))
            val projectsHtml = views.html.projects(projects)
            Ok(views.html.index(tweets, postsHtml, projectsHtml))
        }
    }

    def blog(page: Int) = Action.async {
        getPosts(page) map {
            posts: List[Post] => Ok(views.html.blog(posts, page > 0, postCountLast / maxPosts > page + 1, page))
        }
    }

    def snippets = Action.async {
        compiledTweets("bananafourlife") map {
            statuses: List[Html] => Ok(views.html.snippets(statuses))
        }
    }

    def projects = Cached((x: RequestHeader) => "page.projects", 60 * 60 * 2) {
        Action.async {
            getProjects() map {projects =>
                Ok(views.html.projects(projects))
            }
        }
    }

    def about = Action {
        Ok(views.html.about())
    }
}


