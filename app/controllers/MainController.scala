package controllers

import play.api.cache.Cached
import play.api.libs.json.Json
import play.api.mvc._
import service._

import scala.concurrent.{ExecutionContext, Future}

class MainController(cached: Cached,
                     github: GithubService,
                     tumblr: TumblrService,
                     ldjam: LdjamService,
                     twitch: TwitchService,
                     searchIndex: SearchIndex,
                     implicit val ec: ExecutionContext,
                     components: ControllerComponents) extends AbstractController(components) {

    def index(): Action[AnyContent] = Action.async {
        val future = for {
            curProject <- github.getCurrent
            projects <- github.getProjects
            posts <- tumblr.allPosts
            twitchPlayer <- twitch.getPlayer
        } yield {
            val projectsHtml = projects.map(project => (project.createdAt, views.html.snippet.project(project)))
            val postsHtml = posts.map(post => (post.createdAt, views.html.snippet.blogpost(post, trunc = true)))
            val activities = (postsHtml ++ projectsHtml).sortWith((a, b) => a._1.isAfter(b._1)).map(_._2).take(5)
            curProject match {
                case Some(p) =>
                    ldjam.findEntry(p).map {
                        case Some(node) => Ok(views.html.index(activities, twitchPlayer, Some(views.html.currentproject(p, node))))
                        case None => Ok(views.html.index(activities, twitchPlayer, None))
                    }
                case None => Future.successful(Ok(views.html.index(activities, twitchPlayer, None)))
            }
        }
        future.flatten
    }

    def projects(): EssentialAction = cached((_: RequestHeader) => "page.projects", 60 * 60 * 2) {
        Action.async {
            github.getProjects map { projects =>
                Ok(views.html.projects(projects))
            }
        }
    }

    def about(): Action[AnyContent] = Action {
        Ok(views.html.about())
    }

    def search(query: String): Action[AnyContent] = Action.async {
        for {
            blogPosts <- tumblr.allPosts
            projects <- github.getProjects
        } yield {
            val blogDocs = blogPosts.map(TumblrDoc.apply)
            val projectDocs = projects.map(ProjectDoc.apply)

            val docs: Seq[Doc] = blogDocs ++ projectDocs

            val searchResults = searchIndex.query(docs, query).map(_.toHtml)
            Ok(views.html.blog(searchResults))
        }
    }


    private def simpleSlug(slug: String) = slug.replaceAll("[^A-Za-z0-9]", "").toLowerCase()

    def compareSlugs(p1: String, p2: String): Boolean = simpleSlug(p1) == simpleSlug(p2)

    def project(jam: String, slug: String): Action[AnyContent] = Action.async {
        val jamSlug = simpleSlug(jam)
        val slugSlug = simpleSlug(slug)
        if (jam != jamSlug || slug != slugSlug) Future.successful(Redirect(routes.MainController.project(jamSlug, slugSlug)))
        else github.getProjects.map { projects =>
            projects.find(p => compareSlugs(p.repoName, slug) && compareSlugs(p.jam.map(ji => ji.name).getOrElse("unknown"), jam)) match {
                case Some(p) => Ok(views.html.singleproject(p))
                case None => NotFound(views.html.projects(Seq())) // TODO not found?
            }
        }
    }

    def dev(): Action[AnyContent] = Action.async {

        github.getCurrent.flatMap {
            case Some(project) =>

                ldjam.findEntry(project).map {
                    case Some(post) => Ok(" " + post)
                    case None => NotFound
                }
            case None => Future.successful(NotFound)

        }

    }

    def ldjamIndex(): Action[AnyContent] = Action.async {
        for {
            tags <- ldjam.getFeedOfNodes(0, Seq("all"), "tag", Some("platform"), None, 88)
        } yield {
            val values = Seq(
                Json.toJson(tags),
                Json.toJson(tags.map(_.id).sorted),
                Json.toJson(tags.map(_.id).distinct.sorted),
            )
            Ok(views.html.ldjamevent(values.map(Json.prettyPrint)))
        }

    }

}


