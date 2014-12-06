package controllers

import play.api.Play.current
import play.api.cache.Cached
import play.api.libs.json._
import play.api.libs.ws._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller with Twitter with Tumblr with Youtube with Github {

    def index = Action {
        Ok(views.html.index(compiledTweets("bananafourlife", 4), null, null))
    }

    def blog(page: Int) = Action {
        Ok(views.html.blog(getPosts(page), page > 0, postCountLast / maxPosts > page + 1, page))
    }

    def snippets = Action {
        Ok(views.html.snippets(compiledTweets("bananafourlife")))
    }

    def projects = Cached((x: RequestHeader) => "page.projects", 60 * 60 * 2) {
        Action.async {
            val futureResponse = WS.url("https://api.github.com/orgs/Banana4Life/repos").withRequestTimeout(10000).get()
            val futureProjects = futureResponse.map( response => Json.parse(response.body).as[List[Project]] )
            futureProjects.map( projects => Ok(views.html.projects(projects)) )
        }
    }

    def about = Action {
        Ok(views.html.about())
    }
}

case class Project(name: String)
