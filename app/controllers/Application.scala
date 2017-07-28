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
            projects <- github.getProjects
            posts <- tumblr.getPosts(0)
            tweets <- twitter.compiledTweets("bananafourlife", 9)
            videos <- youtube.getVideos
            twitchPlayer <- twitch.getPlayer
        } yield {
            val projectsHtml = projects.map(project => (project.createdAt, views.html.snippet.project(project)))
            val postsHtml = posts.map(post => (post.createdAt, views.html.snippet.blogpost(post, 0, trunc = true)))
            val videosHtml = videos.map(video => (video.publishedAt, views.html.snippet.youtube(video, video.publishedAt.format(BlogPost.format))))
            val activities = (postsHtml ++ projectsHtml ++ videosHtml).sortWith((a, b) => a._1.isAfter(b._1)).map(_._2).take(5)

            Ok(views.html.index(tweets, activities, twitchPlayer))
        }
    }

    def blog(page: Int) = Action.async {
        val posts: Future[Seq[BlogPost]] = for {
            tumblrPosts <- tumblr.getPosts
            ldjamPosts <- ldjam.getPosts
        } yield tumblrPosts ++ ldjamPosts

        posts.map { posts =>
            val snippets: Seq[Html] = posts.sortBy(-_.createdAt.toEpochSecond) collect {
                case post: TumblrPost =>
                    views.html.snippet.blogpost(post, page, trunc = false)
                case post: LdjamPost =>
                    views.html.snippet.ldjampost(post, trunc = false)
            }

            Ok(views.html.blog(snippets, page > 0, tumblr.postCount.toFloat / tumblr.maxPosts > page + 1, page))
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


