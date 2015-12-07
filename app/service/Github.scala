package service

import java.net.URL
import java.util.Date
import javax.inject.Inject

import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WS}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class ProjectBasics(name: String, html_url: String, full_name: String, pushed_at: Date)

object ProjectBasics {
  implicit val format = Json.format[ProjectBasics]
}

case class LudumDareInfo(number: Int, theme: String, comments: Seq[String])

object LudumDareInfo {
  implicit val format = Json.format[LudumDareInfo]
}

case class ProjectMeta(name: String, description: String, ludumdare: Option[LudumDareInfo], authors: Seq[String])

object ProjectMeta {
  implicit val format = Json.format[ProjectMeta]
}


case class Project(repoName: String, displayName: String, url: URL, description: String,
                   ludumDare: Option[LudumDareInfo], authors: Seq[String], imageUrl: URL,
                   lastUpdated: Date)

class GithubService @Inject() (ws: WSClient) {
  def getProjects(): Future[Seq[Project]] = {
    val futureResponse = ws.url("https://api.github.com/orgs/Banana4Life/repos").withRequestTimeout(10000).get()
    futureResponse flatMap {response =>
      complete(Json.parse(response.body).as[Seq[ProjectBasics]])
    }
  }

  def complete(projectBasics: Seq[ProjectBasics]): Future[Seq[Project]] = {
    val futures = projectBasics map {basics =>
      val metaUrl = s"https://raw.githubusercontent.com/${basics.full_name}/master/.banana4.json"
      val imageUrl = s"https://raw.githubusercontent.com/${basics.full_name}/master/.banana4.png"
      ws.url(metaUrl).get() map {response =>
        val meta = Json.parse(response.body).as[ProjectMeta]
        println(meta.ludumdare)
        Project(basics.name, meta.name, new URL(basics.html_url), meta.description, meta.ludumdare,
          meta.authors, new URL(imageUrl), basics.pushed_at)
      }
    }

    Future.sequence(futures) map {projects =>
      projects.sortBy(_.lastUpdated)
    }
  }
}
