package service

import java.net.URL
import java.time.{Instant, ZoneId, ZonedDateTime}

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.{HttpRequest, HttpRequestInitializer}
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.{YouTube, YouTubeRequestInitializer}
import play.api.{Configuration, Logging}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object DummyInitializer extends HttpRequestInitializer {
    override def initialize(request: HttpRequest): Unit = {}
}

case class YtVideo(id: String, channelName: String, name: String, description: String, thumbnail: URL, publishedAt: ZonedDateTime) {
    lazy val url = new URL(s"https://www.youtube.com/watch?v=$id")
}

class YoutubeService(conf: Configuration, implicit val ec: ExecutionContext) extends Logging{

    private val youtube = {
        val builder = new YouTube.Builder(new NetHttpTransport, new JacksonFactory, DummyInitializer)
            .setApplicationName("banana4.life")

        builder.setYouTubeRequestInitializer(new YouTubeRequestInitializer(conf.get[String]("youtube.apiKey")))
        builder.build()
    }

    private lazy val uploadsPlaylistId: Future[String] = Future {
        val res = youtube.channels()
            .list("contentDetails")
            .setId(conf.get[String]("youtube.channelId"))
            .execute()
        val items = res.getItems
        if (items.size() > 0) {
            items.get(0).getContentDetails.getRelatedPlaylists.getUploads
        } else {
            throw new Exception("No uploads found, correct user?")
        }
    }

    def getVideos: Future[Seq[YtVideo]] = {
        this.uploadsPlaylistId.map(getVideosOfPlaylist)
          .recover { e =>
              logger.error("Failed to get videos!", e)
              Nil
          }
    }

    def getBestThumbnailURL(id: String, thumbs: ThumbnailDetails): URL = {
        val p = Seq(thumbs.getMaxres, thumbs.getHigh, thumbs.getMedium, thumbs.getStandard, thumbs.getDefault)
        new URL(p.filter(_ != null).head.getUrl)
    }

    def getVideosOfPlaylist(id: String): Seq[YtVideo] = {
        val res = youtube.playlistItems().list("snippet").setMaxResults(20.toLong).setPlaylistId(id).execute()
        for (item <- res.getItems.asScala.toSeq) yield {
            val s = item.getSnippet
            val id = s.getResourceId.getVideoId
            YtVideo(id, s.getChannelTitle, s.getTitle, s.getDescription, getBestThumbnailURL(id, s.getThumbnails), ZonedDateTime.ofInstant(Instant.ofEpochMilli(s.getPublishedAt.getValue), ZoneId.systemDefault()))
        }
    }

}
