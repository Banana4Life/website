package service

import java.net.URL

import play.api.libs.json._
import service.LdjamNode.metaFormat

object Formats {
  implicit object UrlFormat extends Format[URL] {
    override def reads(json: JsValue): JsResult[URL] = json match {
      case JsString(s) => JsSuccess(new URL(s))
      case _ => JsError("Not a URL")
    }

    override def writes(o: URL): JsValue = JsString(o.toString)
  }

  implicit object MetaFormat extends Format[Either[LdjamMeta, Int]] {
    override def writes(o: Either[LdjamMeta, Int]): JsValue = o match {
      case Left(meta) => metaFormat.writes(meta)
      case _ => JsArray()
    }

    override def reads(json: JsValue): JsResult[Either[LdjamMeta, Int]] = json match {
      case JsArray(_) => JsSuccess(Right(1))
      case o: JsObject => metaFormat.reads(o).map(Left(_))
      case _ => JsError("LdjamMeta must be an empty array or an object!")
    }
  }

}
