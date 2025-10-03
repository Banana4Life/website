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
import java.time.format.DateTimeFormatter
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

val MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM")

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
                   videos: Seq[Video]) {
    def createdAtMonth(): String = {
        createdAt.format(MONTH_FORMATTER)        
    }    
}

case class Team(name: String, id: Int, slug: String, description: String) derives ConfiguredDecoder
case class Member(login: String, id: Int) derives ConfiguredDecoder
case class User(login: String, name: String, core: Boolean = false)

case class Members(core: Seq[User], guests: Seq[User])

class GithubService(ws: WSClient, cache: SyncCacheApi, config: Configuration, implicit val ec: ExecutionContext) {

    private val logger = Logger(classOf[GithubService])

    private val orga = "Banana4Life"
    private val apiBase = "https://api.github.com"
    private val apiOrgBase = apiBase + "/orgs/"
    private val reposUrl = s"$apiOrgBase$orga/repos"
    private val teamsUrl = s"$apiOrgBase$orga/teams"
    private val graphQlUrl = "https://api.github.com/graphql"
    private val projectJsonFile = ".banana4life/project.json"
    private def teamsMembersUrl(teamId: Int) = s"$apiBase/teams/$teamId/members"
    private def teamsReposUrl(teamId: Int) = s"$apiBase/teams/$teamId/repos"
    private val CoreTeam = "Core"

    implicit val localDateOrdering: Ordering[ZonedDateTime] = Ordering.by(_.toEpochSecond)

    def getCurrent: Future[Option[Project]] = {
        getProjects.map(l => l.headOption.flatMap {
            p => if (p.createdAt.isAfter(ZonedDateTime.now().minus(24, ChronoUnit.DAYS))) Some(p) else None
        })
    }

    def getProjects: Future[Seq[Project]] = {
        cache.getOrElseUpdate(ProjectsCacheKey, CacheDuration) {
            for {
                repos <- getRepos
                repoMembersMap <- getRepoMembersMap
                projects <- Future.sequence(repos.map(repo => getProjectMeta(repo).map((repo, _))))
            } yield {
                projects.filter((_, meta) => meta != null)
                        .map((repo, meta) => {
                              val members = repoMembersMap.getOrElse(repo.name, Members(Seq.empty, Seq.empty))
                              fullProject(repo, members, meta)
                        }).sortBy(_.createdAt)(using localDateOrdering.reverse)
            }
        }
    }

    private def getRepos = {
        ws.url(reposUrl).withRequestTimeout(10000.milliseconds).get().map(_.body[List[Repo]])
    }

    private def fullProject(repo: Repo, members: Members, meta: ProjectMeta): Project = {
        val date = meta.date
          .map(d => d.atStartOfDay(ZoneId.systemDefault()))
          .getOrElse(repo.created_at)

        Project(repo.name,
                meta.name,
                URI(repo.html_url),
                meta.description,
                meta.jam,
                meta.authors.sorted,
                URI(repo.file(".banana4life/main.png")),
                date,
                meta.download.getOrElse(repo.latestRelease),
                meta.soundtrack,
                meta.web,
                meta.cheats.getOrElse(Nil),
                members.core.sortBy(_.name),
                members.guests.sortBy(_.name),
                meta.videos.getOrElse(Seq.empty))
    }

    private def getProjectMeta(repo: Repo) = {
        ws.url(repo.file(projectJsonFile)).get().map { response =>
            response.status match {
                case 200 =>
                    response.body[ProjectMeta]
                case 404 =>
                    logger.info(s"Missing $projectJsonFile for project ${repo.full_name}!")
                    null
                case _ =>
                    logger.warn(s"Failed to request $projectJsonFile for project ${repo.full_name}!")
                    null
            }
        }.recover({
            case _: ParsingFailure =>
                logger.warn(s"Failed to parse $projectJsonFile for project ${repo.full_name}!")
                null
            case e: Exception =>
                logger.error(s"Failed to complete project info for ${repo.full_name}!", e)
                null
        })
    }

    private def withBasicAuth(url: String) = {
        ws.url(url).withAuth(config.get[String]("github.tokenUser"), config.get[String]("github.token"), BASIC)
    }

    private def getRepoMembersMap = {
        if (!config.has("github.tokenUser")) {
            logger.warn("No github token! Cannot fetch team member information!")
            Future.successful(Map.empty[String, Members])
        } else {
            for {
                teams <- getTeams
                members <- Future.sequence(teams.map(team => getTeamMembers(team)))
                repos <- Future.sequence(teams.map(team => getTeamRepos(team)))
                allUsers <- getAllMembers
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

                (for {
                    (team, members, repos) <- allTeams
                    users = getUser(members).partition(_.core)
                    repo <- repos
                } yield {
                    (repo.name, Members(users._1, users._2))
                }).toMap
            }
        }
    }

    private def getTeams = {
        withBasicAuth(teamsUrl).get().map(response => {
            if ((200 to 299) contains response.status) response.body[List[Team]] else Seq()
        })
    }

    private def getTeamRepos(team: Team) = {
        withBasicAuth(teamsReposUrl(team.id)).get().map(_.body[List[Repo]])
    }

    private def getTeamMembers(team: Team) = {
        withBasicAuth(teamsMembersUrl(team.id)).get().map(_.body[List[Member]])
    }

    private def getAllMembers = {
        implicit val bodyWritableOfJson: BodyWritable[Json] = BodyWritable(json => InMemoryBody(ByteString.fromString(json.noSpaces)), "application/json")
        val query = Json.obj("query" -> Json.fromString("query { organization(login:\"Banana4Life\") { membersWithRole(last:100) { nodes { login, name } } } }"))
        withBasicAuth(graphQlUrl).post(query)
          .map(_.json)
          .map { v => v \ "data" \ "organization" \ "membersWithRole" \ "nodes" } collect {
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
