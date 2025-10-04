package controllers.ld58

import controllers.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.libs.json.*
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import service.LdjamService

import scala.concurrent.{ExecutionContext, Future}

private val logger = Logger(classOf[Ld58Controller])

class Ld58Controller(cc: ControllerComponents, ldjam: LdjamService, implicit val ec: ExecutionContext)
                    (implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {


  def stats() = Action.async { request =>
    logger.info(s"${request.connection.remoteAddress} requested stats")
    Future.successful(Ok(Json.toJson("OK")))
  }

  def ldjamIndex(jam: String = "56", username: String = "faithcaio"): Action[AnyContent] = Action.async {
    for {
      jams <- ldjam.walk2(1, s"/events/ludum-dare/$jam")
      user <- ldjam.walk2(1, s"/users/$username")
      // find a bunch of games (limit to 10 for now)
      games <- ldjam.getFeedOfNodes(jams.node_id, Seq("parent"), "item", Some("game"), Some("compo+jam+extra"), 10, 10)
      // find game of user in jam
      userGames <- ldjam.getFeedOfNodes(user.node_id, Seq("authors"), "item", Some("game"), None, 1, 1)
    } yield {
      val values = Json.obj(
          "jam" -> jam,
          "username" -> username,
          "jam_id" -> jams.node_id,
          "user_ud" -> user.node_id,
          // get games with matching jam node_id
          "user_games" -> Json.toJson(userGames.filter(_.parent == jams.node_id).sortBy(_.published)),
          "games" -> Json.toJson(games),
        )
      Ok(values)
    }

  }
}