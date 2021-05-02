package service

import java.net.URL

import play.api.libs.json._

object Formats {

    implicit object UrlFormat extends Format[URL] {
        override def reads(json: JsValue): JsResult[URL] = json match {
            case JsString(s) => JsSuccess(new URL(s))
            case _ => JsError("Not a URL")
        }

        override def writes(o: URL): JsValue = JsString(o.toString)
    }

    implicit val repo: Format[Repo] = Json.format
    implicit val jamInfo: Format[JamInfo] = Json.format
    implicit val webCheat: Format[WebCheat] = Json.format
    implicit val projectMeta: Format[ProjectMeta] = Json.format

    implicit val team: Format[Team] = Json.format
    implicit val member: Format[Member] = Json.format
    implicit val user: Format[User] = Json.format

}
