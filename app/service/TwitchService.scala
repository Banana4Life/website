package service

import javax.inject.Inject

import play.api.Configuration
import play.api.libs.json.{JsDefined, JsString, JsObject, JsSuccess}
import play.api.libs.ws.WSClient
import play.twirl.api.Html

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TwitchService @Inject() (conf: Configuration, client: WSClient) {

  val apiUrl = conf.getString("twitch_stream_url").getOrElse("https://api.twitch.tv/kraken/streams/jonasdann")

  def getPlayer: Future[Option[Html]] = {

    client.url(apiUrl).get() map {response =>
      response.json \ "stream" \ "channel" \ "url" match {
        case JsDefined(JsString(url)) =>
          Some(views.html.twitchplayer(url))
        case _ => None
      }
    }

  }

}