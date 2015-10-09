package service

import play.api.libs.json._
import play.api.libs.ws.WS
import service.github.Project

import scala.concurrent.Future
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

trait Github {
  implicit object ListOfProjectsFormat extends Format[List[Project]] {
    override def writes(projects: List[Project]): JsValue = JsArray(projects.map( project => JsObject(List("name" -> JsString(project.name)))))
    override def reads(json: JsValue): JsResult[List[Project]] = JsSuccess(json.validate[List[JsValue]].get.map( project => Project((project \ "name").validate[String].get) ))
  }

  def getProjects(): Future[List[Project]] = {
    val futureResponse = WS.url("https://api.github.com/orgs/Banana4Life/repos").withRequestTimeout(10000).get()
    futureResponse.map( response => Json.parse(response.body).as[List[Project]] )
  }
}
