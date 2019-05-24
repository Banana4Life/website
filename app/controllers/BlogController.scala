package controllers

import javax.inject.Inject

import play.api.Logger
import play.api.cache.Cached
import play.api.mvc.{AbstractController, ControllerComponents}
import play.twirl.api.Html
import service._

import scala.concurrent.{ExecutionContext, Future}

class BlogController @Inject()(cached: Cached,
                               github: GithubService,
                               tumblr: TumblrService,
                               ldjam: LdjamService,
                               twitter: TwitterService,
                               youtube: YoutubeService,
                               twitch: TwitchService,
                               searchIndex: SearchIndex,
                               implicit val ec: ExecutionContext,
                               components: ControllerComponents) extends AbstractController(components) {

    private val logger = Logger(classOf[BlogController])

    val PostPerPage = 5

    private def orderedPosts(ldjam: Future[Seq[LdjamPost]], tumblr: Future[Seq[TumblrPost]]): Future[Seq[BlogPost]] = {
        for {
            ld <- ldjam
            t <- tumblr
        } yield {
            logger.info(s"LDJam posts found: ${ld.size}, Tumblr: ${t.size}")
            (ld ++ t).sortBy(-_.createdAt.toEpochSecond)
        }
    }

    private def renderPosts(ldjam: Future[Seq[LdjamPost]], tumblr: Future[Seq[TumblrPost]]) = {

        orderedPosts(ldjam, tumblr).map { posts =>

            if (posts.isEmpty) Redirect(routes.BlogController.firstBlogPage())
            else {
                val slice = posts //.take(PostPerPage)

                val snippets: Seq[Html] = slice.map {
                    case post: TumblrPost =>
                        views.html.snippet.blogpost(post, trunc = false)
                    case post: LdjamPost =>
                        views.html.snippet.ldjampost(post, trunc = false)
                }

                if (snippets.isEmpty) Redirect(routes.BlogController.firstBlogPage())
                else Ok(views.html.blog(snippets))
            }

        }
    }

    def firstBlogPage = Action.async {

        val ldjamPosts = ldjam.allPosts
        val tumblrPosts = tumblr.allPosts

        renderPosts(ldjamPosts, tumblrPosts)
    }

    def showPost(service: String, id: Long) = {
        service.toLowerCase match {
            case "ldjam" => showLdjamPost(id.toInt)
            case "tumblr" => showTumblrPost(id)
            case _ => firstBlogPage
        }
    }

    def showLdjamPost(id: Int) = Action.async {

        val ldjamPosts = ldjam.getPost(id).map(_.toSeq)

        renderPosts(ldjamPosts, Future.successful(Nil))

    }

    def showTumblrPost(id: Long) = Action.async {

        val tumblrPosts = tumblr.getPost(id).map(_.toSeq)

        renderPosts(Future.successful(Nil), tumblrPosts)

    }

}
