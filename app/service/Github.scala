package service

import java.net.URL
import java.util.Date
import javax.inject.Inject

import play.api.cache.CacheApi
import play.api.libs.json._
import play.api.libs.ws.WSClient
import service.CacheHelper.{CacheDuration, ProjectsCacheKey}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

case class ProjectBasics(name: String, html_url: String, full_name: String, created_at: Date) {
    def file(path: String, branch: String = "master") = s"https://raw.githubusercontent.com/$full_name/$branch/$path"

    lazy val latestRelease = new URL(s"https://github.com/$full_name/releases/latest")
}

object ProjectBasics {
    implicit val format = Json.format[ProjectBasics]
}

case class JamInfo(name: String, number: Int, theme: String, site: String, comments: Seq[String])

object JamInfo {
    implicit val format = Json.format[JamInfo]
}

case class ProjectMeta(name: String, description: String, jam: Option[JamInfo], authors: Seq[String],
                       download: Option[String], soundtrack: Option[String], date: Option[Date])

object ProjectMeta {
    implicit val format = Json.format[ProjectMeta]
}


case class Project(repoName: String, displayName: String, url: URL, description: String,
                   jam: Option[JamInfo], authors: Seq[String], imageUrl: URL,
                   createdAt: Date, download: URL, soundtrack: Option[URL])

class GithubService @Inject()(ws: WSClient, cache: CacheApi) {

    private val orga = "Banana4Life"
    private val reposUrl = s"https://api.github.com/orgs/$orga/repos"

    def getProjects: Future[Seq[Project]] = {
        cache.getOrElse(ProjectsCacheKey, CacheDuration) {
            val futureResponse = ws.url(reposUrl).withRequestTimeout(10000.milliseconds).get()
            futureResponse flatMap { response =>
                complete(Json.parse(response.body).as[Seq[ProjectBasics]])
            }
        }
    }

    def complete(projectBasics: Seq[ProjectBasics]): Future[Seq[Project]] = {
        val futures = projectBasics map { basics =>
            ws.url(basics.file(".banana4.json")).get().map { response =>
                val meta = Json.parse(response.body).as[ProjectMeta]
                Project(basics.name, meta.name, new URL(basics.html_url), meta.description, meta.jam,
                    meta.authors, new URL(basics.file(".banana4.png")), meta.date.getOrElse(basics.created_at),
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
