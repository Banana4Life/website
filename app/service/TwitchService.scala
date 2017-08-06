package service

import javax.inject.Inject

import play.api.Configuration
import play.api.libs.json.{JsDefined, JsString}
import play.api.libs.ws.WSClient
import play.twirl.api.Html

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class TwitchService @Inject()(conf: Configuration, client: WSClient, implicit val ec: ExecutionContext) {

    private val apiUrl = conf.getOptional[String]("twitch_stream_url").getOrElse("https://api.twitch.tv/kraken/streams/bananafourlife")

    def getPlayer: Future[Option[Html]] = {

        client.url(apiUrl).withRequestTimeout(2.seconds).get() map { response =>
            response.json \ "stream" \ "channel" \ "url" match {
                case JsDefined(JsString(url)) =>
                    Some(views.html.twitchplayer(url))
                case _ => None
            }
        } recover { case _ => None }

    }

}
