package service

import io.circe
import io.circe.derivation.ConfiguredDecoder
import io.circe.{Decoder, Json, ParsingFailure, derivation}
import org.apache.pekko.util.ByteString
import play.api.cache.SyncCacheApi
import play.api.libs.json.{JsArray, JsDefined, JsNull, JsString}
import play.api.libs.ws.WSAuthScheme.BASIC
import play.api.libs.ws.{BodyWritable, InMemoryBody, WSClient}
import play.api.{Configuration, Logger}
import service.CacheHelper.{CacheDuration, ProjectsCacheKey}

import java.net.URI
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

given derivation.Configuration = derivation.Configuration.default.withDefaults

case class Repo(name: String, html_url: String, full_name: String, created_at: ZonedDateTime, default_branch: String) derives ConfiguredDecoder {
    def file(path: String, branch: String = default_branch) = s"https://raw.githubusercontent.com/$full_name/$branch/$path"

    lazy val latestRelease = URI(s"https://github.com/$full_name/releases/latest")
}

case class JamInfo(name: String, number: Int, theme: String, site: URI, comments: Seq[String]) derives ConfiguredDecoder {
  def fixedSite(): URI = {
      if (site.getHost == "ludumdare.com")
          URI(s"https://web.archive.org/web/2022/${site.toString}")
      else site
  }
} 

case class WebCheat(label: String, gameObject: String, message: String) derives ConfiguredDecoder


case class Video(`type`: String = "youtube", url: String, title: String = "") derives ConfiguredDecoder {
    def displayTitle(): String = if (title.isBlank) url else title
}

case class ProjectMeta(name: String,
                       description: String,
                       jam: Option[JamInfo],
                       authors: Seq[String],
                       download: Option[URI],
                       soundtrack: Option[URI],
                       date: Option[LocalDate],
                       web: Option[URI],
                       cheats: Option[Seq[WebCheat]],
                       videos: Option[Seq[Video]]
                      ) derives Decoder

case class Project(repoName: String,
                   displayName: String,
                   url: URI,
                   description: String,
                   jam: Option[JamInfo],
                   authors: Seq[String],
                   imageUrl: URI,
                   createdAt: ZonedDateTime,
                   download: URI,
                   soundtrack: Option[URI],
                   web: Option[URI],
                   cheats: Seq[WebCheat],
                   coreUsers: Seq[User],
                   guests: Seq[User],
                   videos: Seq[Video])

case class Team(name: String, id: Int, slug: String, description: String) derives ConfiguredDecoder
case class Member(login: String, id: Int) derives ConfiguredDecoder
case class User(login: String, name: String, core: Boolean = false)

class GithubService(ws: WSClient, cache: SyncCacheApi, config: Configuration, implicit val ec: ExecutionContext) {

    private val logger = Logger(classOf[GithubService])

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
                responseRepos <- ws.url(reposUrl).withRequestTimeout(10000.milliseconds).get()
                teams <- getTeams
                projects <- complete(responseRepos.body[List[Repo]], teams)
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

        val memberLookup = (for {
            (_, u, r) <- teams
            repo <- r
        } yield {
            (repo.name, u)
        }).toMap
        val futures = projectBasics map { basics =>
            val fileName = ".banana4life/project.json"
            given derivation.Configuration = derivation.Configuration.default.withDefaults
            ws.url(basics.file(fileName)).get().map { response =>
                response.status match {
                    case 200 =>
                        val meta = response.body[ProjectMeta]
                        val date = meta.date
                          .map(d => d.atStartOfDay(ZoneId.systemDefault()))
                          .getOrElse(basics.created_at)
                        val (core, guests) = memberLookup.getOrElse(basics.name, (Seq.empty, Seq.empty))
                        Project(basics.name, meta.name, URI(basics.html_url), meta.description, meta.jam,
                            meta.authors.sorted, URI(basics.file(".banana4life/main.png")), date,
                            meta.download.getOrElse(basics.latestRelease), meta.soundtrack, meta.web, meta.cheats.getOrElse(Nil),
                            core.sortBy(_.name), guests.sortBy(_.name), meta.videos.getOrElse(Seq.empty))
                    case 404 =>
                        logger.info(s"Missing $fileName for project ${basics.full_name}!")
                        null
                    case _ =>
                        logger.warn(s"Failed to request $fileName for project ${basics.full_name}!")
                        null
                }
            }.recover({
                case _: ParsingFailure =>
                    logger.warn(s"Failed to parse $fileName for project ${basics.full_name}!")
                    null
                case e: Exception =>
                    logger.error(s"Failed to complete project info for ${basics.full_name}!", e)
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
            response <- withAuth(teamsUrl)
            teams = if ((200 to 299) contains response.status) response.body[List[Team]] else Seq()
            members <- Future.sequence(teams.map(team => withAuth(teamsMembersUrl(team.id)).map(_.body[List[Member]])))
            repos <- Future.sequence(teams.map(team => withAuth(teamsReposUrl(team.id)).map(_.body[List[Repo]])))
            allUsers <- getMembers
        } yield {
            val allTeams = teams zip members zip repos map {
                case ((t, m), r) => (t, m, r)
            }
            val coreTeam = allTeams.find(_._1.name == CoreTeam).map(_._2).getOrElse(Set.empty[Member]).map(_.login).toSet

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
        implicit val bodyWritableOfJson: BodyWritable[Json] = BodyWritable(json => InMemoryBody(ByteString.fromString(json.noSpaces)), "application/json")
        val query = Json.obj("query" -> Json.fromString("query { organization(login:\"Banana4Life\") { membersWithRole(last:100) { nodes { login, name } } } }"))
        ws.url("https://api.github.com/graphql").withAuth(config.get[String]("github.tokenUser"), config.get[String]("github.token"), BASIC)
          .post(query)
          .map(_.json)
          .map { v =>
                logger.info(v.toString())
                v \ "data" \ "organization" \ "membersWithRole" \ "nodes"
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
                        case _ => login
                    }

                    User(login.as[String], name.as[String])
                  }
                case _ => Seq.empty
            } map { users =>
                users.map(user => (user.login, user)).toMap
            }
    }
}
