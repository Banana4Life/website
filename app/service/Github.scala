package service

import java.net.URL
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneId, ZonedDateTime}

import com.fasterxml.jackson.core.JsonParseException
import javax.inject.{Inject, Singleton}
import play.api.cache.SyncCacheApi
import play.api.libs.json._
import play.api.libs.ws.WSAuthScheme.BASIC
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}
import service.CacheHelper.{CacheDuration, ProjectsCacheKey}
import service.Formats._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

case class Repo(name: String, html_url: String, full_name: String, created_at: ZonedDateTime) {
    def file(path: String, branch: String = "master") = s"https://raw.githubusercontent.com/$full_name/$branch/$path"

    lazy val latestRelease = new URL(s"https://github.com/$full_name/releases/latest")
}

case class JamInfo(name: String, number: Int, theme: String, site: URL, comments: Seq[String])

case class WebCheat(label: String, gameObject: String, message: String)

case class ProjectMeta(name: String, description: String, jam: Option[JamInfo], authors: Seq[String],
                       download: Option[URL], soundtrack: Option[URL], date: Option[LocalDate], web: Option[URL], cheats: Option[Seq[WebCheat]])

case class Project(repoName: String, displayName: String, url: URL, description: String,
                   jam: Option[JamInfo], authors: Seq[String], imageUrl: URL,
                   createdAt: ZonedDateTime, download: URL, soundtrack: Option[URL], web: Option[URL], cheats: Seq[WebCheat],
                   coreUsers: Seq[User], guests: Seq[User])

case class Team(name: String, id: Int, slug: String, description: String)
case class Member(login: String, id: Int)
case class User(login: String, name: String, core: Boolean = false)

@Singleton
class GithubService @Inject()(ws: WSClient, cache: SyncCacheApi, config: Configuration, implicit val ec: ExecutionContext) {

    private val orga = "Banana4Life"
    private val apiBase = "https://api.github.com"
    private val apiOrgBase = apiBase + "/orgs/"
    private val reposUrl = s"$apiOrgBase$orga/repos"
    private val teamsUrl = s"$apiOrgBase$orga/teams"
    private def teamsMembersUrl(teamId: Int) = s"$apiBase/teams/$teamId/members"
    private def teamsReposUrl(teamId: Int) = s"$apiBase/teams/$teamId/repos"
    private val CoreTeam = "Core"


    implicit val localDateOrdering: Ordering[ZonedDateTime] = Ordering.by(_.toEpochSecond)

    def getProjects: Future[Seq[Project]] = {
        cache.getOrElseUpdate(ProjectsCacheKey, CacheDuration) {
            for {
                repos <- ws.url(reposUrl).withRequestTimeout(10000.milliseconds).get()
                teams <- getTeams
                projects <- complete(repos.json.as[Seq[Repo]], teams)
            } yield {
                projects
            }
        }
    }

    def getCurrent: Future[Option[Project]] = {
        getProjects.map(l => l.headOption.flatMap {
            p => if (p.createdAt.isAfter(ZonedDateTime.now().minus(24, ChronoUnit.DAYS))) Some(p) else None
        })

    }


    def complete(projectBasics: Seq[Repo], teams: Seq[(Team, (Seq[User], Seq[User]), Seq[Repo])]): Future[Seq[Project]] = {

        val repoMembers = for {
            (_, u, r) <- teams
            repo <- r
        }
        yield {
            (repo.name, u)
        }
        val memberLookuqp = repoMembers.toMap
        val futures = projectBasics map { basics =>
            val fileName = ".banana4life/project.json"
            ws.url(basics.file(fileName)).get().map { response =>

                val meta = Json.parse(response.body).as[ProjectMeta]
                val date = meta.date
                    .map(d => d.atStartOfDay(ZoneId.systemDefault()))
                    .getOrElse(basics.created_at)
                val (core, guests) = memberLookuqp.getOrElse(basics.name, (Seq.empty, Seq.empty))
                Project(basics.name, meta.name, new URL(basics.html_url), meta.description, meta.jam,
                    meta.authors, new URL(basics.file(".banana4life/main.png")), date,
                    meta.download.getOrElse(basics.latestRelease), meta.soundtrack, meta.web, meta.cheats.getOrElse(Nil),
                    core, guests)
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

    def getTeams = {

        def withAuth(url: String) = {
            ws.url(url).withAuth(config.get[String]("github.tokenUser"), config.get[String]("github.token"), BASIC).get()
        }

        for {
            teams <- withAuth(teamsUrl).map(_.json.as[Seq[Team]])
            members <- Future.sequence(teams.map(team => withAuth(teamsMembersUrl(team.id)).map(_.json.as[Seq[Member]])))
            repos <- Future.sequence(teams.map(team => withAuth(teamsReposUrl(team.id)).map(_.json.as[Seq[Repo]])))
            allUsers <- getMembers
        } yield {
            val allTeams = teams zip members zip repos map {
                case ((t, m), r) => (t, m, r)
            }
            val coreTeam = allTeams.find(_._1.name == CoreTeam).map(_._2).getOrElse(Set.empty).map(_.login).toSet

            def getUser(m: Seq[Member]) = {
                m.map { mm =>
                    val name = allUsers.get(mm.login).map(_.name).getOrElse(mm.login)
                    User(mm.login, name, coreTeam.contains(mm.login))
                }
            }

            allTeams.map {
                case (t, m, r) => (t, getUser(m).partition(_.core), r)
            }
        }
    }

    def getMembers = {
        ws.url("https://api.github.com/graphql").withAuth(config.get[String]("github.tokenUser"), config.get[String]("github.token"), BASIC).post(
            Json.obj("query" -> "query { organization(login:\"Banana4Life\") { members(last:100) { nodes { login, name } } } }")
          ).map(_.json).map { v =>
            Logger.info(v.toString())
            v \ "data" \ "organization" \ "members" \ "nodes"
        } collect {
            case JsDefined(JsArray(nodes)) => nodes.map { value =>
                val login = (value \ "login").get
                val name = value \ "name" match {
                    case JsDefined(n) =>
                        n match {
                            case JsNull => login
                            case JsString("") => login
                            case _ => n
                        }
                    case JsUndefined() => login
                }

                User(login.as[String], name.as[String])
              }
            case _ => Seq.empty
        } map { users =>
            users.map(user => (user.login, user)).toMap
        }

    }


}
