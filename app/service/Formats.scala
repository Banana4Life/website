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



}
