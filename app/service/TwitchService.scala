package service

import io.circe.derivation
import io.circe.derivation.ConfiguredCodec
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.twirl.api.Html

import java.net.URI
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class TwitchService(conf: Configuration, client: WSClient, implicit val ec: ExecutionContext) {

    private val apiUrl = conf.getOptional[String]("twitch_stream_url").getOrElse("https://api.twitch.tv/kraken/streams/bananafourlife")

    def getPlayer: Future[Option[Html]] = {

        client.url(apiUrl).withRequestTimeout(2.seconds).get() map { response =>
            Some(views.html.twitchplayer(response.body[TwitchGetStreamReply].stream.channel.url ))
        } recover { case _ => None }

    }
}

private final case class TwitchGetStreamReply(stream: TwitchStream) derives ConfiguredCodec
private final case class TwitchStream(channel: TwitchChannel) derives ConfiguredCodec
private final case class TwitchChannel(url: URI) derives ConfiguredCodec