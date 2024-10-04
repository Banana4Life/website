package service

import java.net.URI
import play.api.libs.json.*

object Formats {

    implicit object UrlFormat extends Format[URI] {
        override def reads(json: JsValue): JsResult[URI] = json match {
            case JsString(s) => JsSuccess(URI(s))
            case _ => JsError("Not a URL")
        }

        override def writes(o: URI): JsValue = JsString(o.toString)
    }

    implicit val repo: Format[Repo] = Json.format
    implicit val jamInfo: Format[JamInfo] = Json.format
    implicit val webCheat: Format[WebCheat] = Json.format
    implicit val projectMeta: Format[ProjectMeta] = Json.format

    implicit val team: Format[Team] = Json.format
    implicit val member: Format[Member] = Json.format
    implicit val user: Format[User] = Json.format

}
