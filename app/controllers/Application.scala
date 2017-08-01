package controllers

import javax.inject.Inject

import play.api.cache.Cached
import play.api.mvc._
import play.twirl.api.Html
import service._

import scala.concurrent.{ExecutionContext, Future}

class Application @Inject() (cached: Cached,
                             github: GithubService,
                             tumblr: TumblrService,
                             ldjam: LdjamService,
                             twitter: TwitterService,
                             youtube: YoutubeService,
                             twitch: TwitchService,
                             searchIndex: SearchIndex,
                             implicit val ec: ExecutionContext,
                             components: ControllerComponents) extends AbstractController(components) {

    def index = Action.async {
        for {
            curProject <- github.getCurrent
            projects <- github.getProjects.map(_.filterNot(curProject.contains))
            posts <- tumblr.getPosts(0)
            tweets <- twitter.compiledTweets("bananafourlife", 9)
            videos <- youtube.getVideos
            twitchPlayer <- twitch.getPlayer
        } yield {
            val projectsHtml = projects.map(project => (project.createdAt, views.html.snippet.project(project)))
            val postsHtml = posts.map(post => (post.createdAt, views.html.snippet.blogpost(post, trunc = true)))
            val videosHtml = videos.map(video => (video.publishedAt, views.html.snippet.youtube(video, video.publishedAt.format(BlogPost.format))))
            val activities = (postsHtml ++ projectsHtml ++ videosHtml).sortWith((a, b) => a._1.isAfter(b._1)).map(_._2).take(5)
            val currentProject = curProject.map(p => views.html.snippet.project(p))

            Ok(views.html.index(tweets, activities, twitchPlayer, currentProject))
        }
    }

    val PostPerPage = 5

    def blog(page: Int) = Action.async {
        val posts: Future[Seq[BlogPost]] = for {
            tumblrPosts <- tumblr.getPosts
            ldjamPosts <- ldjam.getPosts
        } yield tumblrPosts ++ ldjamPosts

        posts.map { posts =>

            if (posts.isEmpty) Redirect(routes.Application.projects())
            else {
                val skip = PostPerPage * (page - 1)
                val snippets: Seq[Html] = posts.sortBy(-_.createdAt.toEpochSecond).slice(skip, skip + PostPerPage).collect {
                    case post: TumblrPost =>
                        views.html.snippet.blogpost(post, trunc = false)
                    case post: LdjamPost =>
                        views.html.snippet.ldjampost(post, trunc = false)
                }

                if (snippets.isEmpty) Redirect(routes.Application.blog(1))
                else Ok(views.html.blog(snippets, page > 1, posts.length.toFloat / PostPerPage > page, page))
            }

        }

    }

    def projects = cached((x: RequestHeader) => "page.projects", 60 * 60 * 2) {
        Action.async {
            github.getProjects map {projects =>
                Ok(views.html.projects(projects))
            }
        }
    }

    def about = Action {
        Ok(views.html.about())
    }

    def search(query: String) = Action.async {
        for {
            blogPosts <- tumblr.getPosts
            projects <- github.getProjects
        } yield {
            val blogDocs = blogPosts.map(TumblrDoc)
            val projectDocs = projects.map(ProjectDoc)

            val docs: Seq[Doc] = blogDocs ++ projectDocs

            val searchResults = searchIndex.query(docs, query).map(_.toHtml)
            Ok(views.html.blog(searchResults, prev = false, next = false, 0))
        }
    }
}


