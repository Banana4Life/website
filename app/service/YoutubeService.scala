package service

import java.net.URL
import javax.inject.Inject

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.{HttpRequest, HttpRequestInitializer}
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.{YouTube, YouTubeRequestInitializer}
import play.api.Configuration

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object DummyInitializer extends HttpRequestInitializer {
  override def initialize(request: HttpRequest): Unit = {}
}

case class YtVideo(id: String, name: String, description: String, thumbnail: URL, publishedAt: DateTime) {
  lazy val url = new URL(s"https://www.youtube.com/watch?v=$id")
}

class YoutubeService @Inject() (conf: Configuration) {

  private val youtube = {
    val builder = new YouTube.Builder(new NetHttpTransport, new JacksonFactory, DummyInitializer)
      .setApplicationName("banana4.life")

    for (apikey <- conf.getString("youtube.apikey")) {
      builder.setYouTubeRequestInitializer(new YouTubeRequestInitializer(apikey))
    }

    builder.build()
  }

  private lazy val uploadsPlaylistId: Future[String] = Future {
    val channelId = conf.getString("youtube.channelId")
    if (channelId.isEmpty) {
      throw new Exception("No channel ID configured!")
    }
    val res = youtube.channels()
      .list("contentDetails")
      .setId(channelId.get)
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
  }

  def getBestThumbnailURL(id: String, thumbs: ThumbnailDetails): URL = {
    val p = Seq(thumbs.getMaxres, thumbs.getHigh, thumbs.getMedium, thumbs.getStandard, thumbs.getDefault)
    new URL(p.filter(_ != null).head.getUrl)
  }

  def getVideosOfPlaylist(id: String): Seq[YtVideo] = {
    val res = youtube.playlistItems().list("snippet").setMaxResults(20.toLong).setPlaylistId(id).execute()
    for (item <- res.getItems.asScala) yield {
      val s = item.getSnippet
      val id = s.getResourceId.getVideoId
      YtVideo(id, s.getTitle, s.getDescription, getBestThumbnailURL(id, s.getThumbnails), s.getPublishedAt)
    }
  }

}
