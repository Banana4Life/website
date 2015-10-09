package service

import javax.inject.Inject

import play.api.libs.json._
import play.api.libs.ws.{WSClient, WS}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class Project(name: String)

object Project {
  implicit val format = Json.format[Project]
}

class GithubService @Inject() (ws: WSClient) {
  def getProjects(): Future[List[Project]] = {
    val futureResponse = ws.url("https://api.github.com/orgs/Banana4Life/repos").withRequestTimeout(10000).get()
    futureResponse map {response =>
      Json.parse(response.body).as[List[Project]]
    }
  }
}
