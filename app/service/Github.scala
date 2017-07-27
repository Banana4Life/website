package service

import java.net.URL
import java.time.ZonedDateTime
import javax.inject.Inject

import play.api.cache.SyncCacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient
import service.CacheHelper.{CacheDuration, ProjectsCacheKey}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

case class ProjectBasics(name: String, html_url: String, full_name: String, created_at: ZonedDateTime) {
    def file(path: String, branch: String = "master") = s"https://raw.githubusercontent.com/$full_name/$branch/$path"

    lazy val latestRelease = new URL(s"https://github.com/$full_name/releases/latest")
}

object ProjectBasics {
    implicit val format: Format[ProjectBasics] = Json.format
}

case class JamInfo(name: String, number: Int, theme: String, site: String, comments: Seq[String])

object JamInfo {
    implicit val format: Format[JamInfo] = Json.format
}

case class ProjectMeta(name: String, description: String, jam: Option[JamInfo], authors: Seq[String],
                       download: Option[String], soundtrack: Option[String], date: Option[ZonedDateTime])

object ProjectMeta {
    implicit val format: Format[ProjectMeta] = Json.format
}


case class Project(repoName: String, displayName: String, url: URL, description: String,
                   jam: Option[JamInfo], authors: Seq[String], imageUrl: URL,
                   createdAt: ZonedDateTime, download: URL, soundtrack: Option[URL])

class GithubService @Inject()(ws: WSClient, cache: SyncCacheApi, implicit val ec: ExecutionContext) {

    private val orga = "Banana4Life"
    private val reposUrl = s"https://api.github.com/orgs/$orga/repos"

    def getProjects: Future[Seq[Project]] = {
        cache.getOrElseUpdate(ProjectsCacheKey, CacheDuration) {
            val futureResponse = ws.url(reposUrl).withRequestTimeout(10000.milliseconds).get()
            futureResponse flatMap { response =>
                complete(Json.parse(response.body).as[Seq[ProjectBasics]])
            }
        }
    }

    implicit val localDateOrdering: Ordering[ZonedDateTime] = Ordering.by(_.toEpochSecond)

    def complete(projectBasics: Seq[ProjectBasics]): Future[Seq[Project]] = {
        val futures = projectBasics map { basics =>
            ws.url(basics.file(".banana4.json")).get().map { response =>
                val meta = Json.parse(response.body).as[ProjectMeta]
                Project(basics.name, meta.name, new URL(basics.html_url), meta.description, meta.jam,
                    meta.authors, new URL(basics.file("banana4life/main.png")), meta.date.getOrElse(basics.created_at),
                    meta.download.map(new URL(_)).getOrElse(basics.latestRelease), meta.soundtrack.map(new URL(_)))
            }.recover({
                case e: Exception => println(e)
                    null
            })
        }

        Future.sequence(futures) map { projects =>
            projects.filter(_ != null).sortBy(_.createdAt).reverse
        }
    }
}
