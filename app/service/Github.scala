package service

import java.net.URL
import java.util.Date
import javax.inject.Inject

import play.api.libs.json._
import play.api.libs.ws.WSClient

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

case class LudumDareInfo(number: Int, theme: String, site: String, comments: Seq[String])

object LudumDareInfo {
  implicit val format = Json.format[LudumDareInfo]
}

case class ProjectMeta(name: String, description: String, ludumdare: Option[LudumDareInfo], authors: Seq[String],
                      download: Option[String], soundtrack: Option[String])

object ProjectMeta {
  implicit val format = Json.format[ProjectMeta]
}


case class Project(repoName: String, displayName: String, url: URL, description: String,
                   ludumDare: Option[LudumDareInfo], authors: Seq[String], imageUrl: URL,
                   createdAt: Date, download: URL, soundtrack: Option[URL])

class GithubService @Inject() (ws: WSClient) {
  def getProjects: Future[Seq[Project]] = {
    val futureResponse = ws.url("https://api.github.com/orgs/Banana4Life/repos").withRequestTimeout(10000.milliseconds).get()
    futureResponse flatMap {response =>
      complete(Json.parse(response.body).as[Seq[ProjectBasics]])
    }
  }

  def complete(projectBasics: Seq[ProjectBasics]): Future[Seq[Project]] = {
    val futures = projectBasics map {basics =>
      ws.url(basics.file(".banana4.json")).get().map {response =>
        val meta = Json.parse(response.body).as[ProjectMeta]
        Project(basics.name, meta.name, new URL(basics.html_url), meta.description, meta.ludumdare,
          meta.authors, new URL(basics.file(".banana4.png")), basics.created_at,
          meta.download.map(new URL(_)).getOrElse(basics.latestRelease), meta.soundtrack.map(new URL(_)))
      }.recover({
        case e: Exception => println(e)
              null
      })
    }

    Future.sequence(futures) map {projects =>
        projects.filter(_ != null).sortBy(_.createdAt).reverse
      }
    }
}
