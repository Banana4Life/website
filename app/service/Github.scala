package service

import java.net.URL
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneId, ZonedDateTime}
import javax.inject.Inject

import com.fasterxml.jackson.core.JsonParseException
import play.api.Logger
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

case class WebCheat(label: String, gameObject: String, message: String)

object WebCheat {
    implicit val format: Format[WebCheat] = Json.format
}

case class ProjectMeta(name: String, description: String, jam: Option[JamInfo], authors: Seq[String],
                       download: Option[String], soundtrack: Option[String], date: Option[LocalDate], web: Option[String], cheats: Option[Seq[WebCheat]])

object ProjectMeta {
    implicit val format: Format[ProjectMeta] = Json.format
}

case class Project(repoName: String, displayName: String, url: URL, description: String,
                   jam: Option[JamInfo], authors: Seq[String], imageUrl: URL,
                   createdAt: ZonedDateTime, download: URL, soundtrack: Option[URL], web: Option[URL], cheats: Seq[WebCheat])

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

    def getCurrent: Future[Option[Project]] = {
        getProjects.map(l => l.headOption.flatMap{
            p => if (p.createdAt.isAfter(ZonedDateTime.now().minus(24, ChronoUnit.DAYS))) Some(p) else None
        })
    }

    implicit val localDateOrdering: Ordering[ZonedDateTime] = Ordering.by(_.toEpochSecond)

    def complete(projectBasics: Seq[ProjectBasics]): Future[Seq[Project]] = {
        val futures = projectBasics map { basics =>
            val fileName = ".banana4life/project.json"
            ws.url(basics.file(fileName)).get().map { response =>

                val meta = Json.parse(response.body).as[ProjectMeta]
                val date = meta.date
                    .map(d => d.atStartOfDay(ZoneId.systemDefault()))
                    .getOrElse(basics.created_at)

                Project(basics.name, meta.name, new URL(basics.html_url), meta.description, meta.jam,
                    meta.authors, new URL(basics.file(".banana4life/main.png")), date,
                    meta.download.map(new URL(_)).getOrElse(basics.latestRelease), meta.soundtrack.map(new URL(_)),
                    meta.web.map(new URL(_)), meta.cheats.getOrElse(Nil))
            }.recover({
                case _: JsonParseException =>
                    Logger.warn(s"Failed to parse $fileName for project ${basics.full_name}!")
                    null
                case e: Exception =>
                    Logger.error(s"Failed to complete project info for ${basics.full_name}!", e)
                    null
            })
        }

        Future.sequence(futures) map { projects =>
            projects.filter(_ != null).sortBy(_.createdAt).reverse
        }
    }

    def getWebVersion() = {
        ws.url("https://banana4life.github.io/LegendarySpaceSpaceSpace/latest/").get()
    }
}
