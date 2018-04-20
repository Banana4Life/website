package service

import java.net.URL

import play.api.libs.json._

object Formats {

    implicit val ldjamMeta: Format[LdjamMeta] = Json.format

    implicit object UrlFormat extends Format[URL] {
        override def reads(json: JsValue): JsResult[URL] = json match {
            case JsString(s) => JsSuccess(new URL(s))
            case _ => JsError("Not a URL")
        }

        override def writes(o: URL): JsValue = JsString(o.toString)
    }

    implicit object MetaFormat extends Format[Either[LdjamMeta, Int]] {
        override def writes(o: Either[LdjamMeta, Int]): JsValue = o match {
            case Left(meta) => ldjamMeta.writes(meta)
            case _ => JsArray()
        }

        override def reads(json: JsValue): JsResult[Either[LdjamMeta, Int]] = json match {
            case JsArray(_) => JsSuccess(Right(1))
            case o: JsObject => ldjamMeta.reads(o).map(Left(_))
            case _ => JsError("LdjamMeta must be an empty array or an object!")
        }
    }

    implicit val ldjamNode: Format[LdjamNode] = Json.format
    implicit val repo: Format[Repo] = Json.format
    implicit val jamInfo: Format[JamInfo] = Json.format
    implicit val webCheat: Format[WebCheat] = Json.format
    implicit val projectMeta: Format[ProjectMeta] = Json.format

    implicit val team: Format[Team] = Json.format
    implicit val member: Format[Member] = Json.format
    implicit val user: Format[User] = Json.format

}
