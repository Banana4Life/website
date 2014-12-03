package controllers

import play.api.libs.json._

trait Github {
  implicit object ListOfProjectsFormat extends Format[List[Project]] {
    override def writes(projects: List[Project]): JsValue = JsArray(projects.map( project => JsObject(List("name" -> JsString(project.name)))))
    override def reads(json: JsValue): JsResult[List[Project]] = JsSuccess(json.validate[List[JsValue]].get.map( project => Project((project \ "name").validate[String].get) ))
  }
}
