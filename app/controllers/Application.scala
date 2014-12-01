package controllers

import java.util

import com.tumblr.jumblr.JumblrClient
import play.api.Play
import play.api.libs.json._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.ws._

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

object Application extends Controller with Twitter {
    val maxPosts = 1

    implicit object ListOfProjectsFormat extends Format[List[Project]] {
        override def writes(projects: List[Project]): JsValue = JsArray(projects.map( project => JsObject(List("name" -> JsString(project.name)))))
        override def reads(json: JsValue): JsResult[List[Project]] = JsSuccess(json.validate[List[JsValue]].get.map( project => Project((project \ "name").validate[String].get) ))
    }

    def index = Action {
        Ok(views.html.index("Index"))
    }

    def blog(page: Int) = Action {
        val client = new JumblrClient(Play.configuration.getString("secret.customerkey").get, Play.configuration.getString("secret.customersecret").get)
        val postCount = client.blogInfo("bananafourlife").getPostCount
        val options = new util.HashMap[String, Int]()
        options.put("limit", maxPosts)
        options.put("offset", page * maxPosts)
        Ok(views.html.blog(client.blogPosts("bananafourlife", options), page > 0, postCount / maxPosts > page + 1, page))
    }

    def snippets = Action {
        Ok(views.html.snippets(compiledTweets("bananafourlife")))
    }

    def projects = Action.async {
        val futureResponse = WS.url("https://api.github.com/orgs/Banana4Life/repos").withRequestTimeout(10000).get()
        val futureProjects = futureResponse.map( response => Json.parse(response.body).as[List[Project]] )
        futureProjects.map( projects => Ok(views.html.projects(projects)) )
    }

    def about = Action {
        Ok(views.html.about())
    }
}

case class Project(name: String) {

}
