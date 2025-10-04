package controllers.ld58

import controllers.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.libs.json.*
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import service.{EventNode, GameNode, LdjamService, Node}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

private val logger = Logger(classOf[Ld58Controller])

class Ld58Controller(cc: ControllerComponents, ldjam: LdjamService, implicit val ec: ExecutionContext)
                    (implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {


  def stats() = Action.async { request =>
    logger.info(s"${request.connection.remoteAddress} requested stats")
    Future.successful(Ok(Json.toJson("OK")))
  }

  def ldjamIndex(jam: String = "58", username: String = "faithcaio"): Action[AnyContent] = Action.async {
    for {
      jams <- ldjam.walk2(1, s"/events/ludum-dare/$jam")
      user <- ldjam.walk2(1, s"/users/$username")
      jam <- ldjam.getNodes2(Seq(jams.node_id))
      // find a bunch of games (limit to 10 for now)
      games <- ldjam.getFeedOfNodes(jams.node_id, Seq("parent"), "item", Some("game"), Some("compo+jam+extra"), 10, 10)
      // find game of user in jam
      userGames <- ldjam.getFeedOfNodes(user.node_id, Seq("authors"), "item", Some("game"), None, 10, 1)
    } yield {
      val userGamesFiltered = games.flatMap {
        case gn: GameNode if gn.meta.cover.nonEmpty => Some(gn)
        case _ => None
      }.map(gn => Json.obj(
        "id" -> gn.id,
        "name" -> gn.name,
        "cover" -> gn.meta.cover.map(cover => ldjam.cdnUrl(cover.replace("///", "/") + ".480x384.fit.jpg")).orNull,
        "web" -> {
          // TODO report non-embeddable game
          if (gn.meta.`link-01-tag`.map(_.as[Seq[Int]]).getOrElse(Seq.empty).contains(42336)) gn.meta.`link-01`.orNull else
          if (gn.meta.`link-02-tag`.map(_.as[Seq[Int]]).getOrElse(Seq.empty).contains(42336)) gn.meta.`link-02`.orNull else null
        },
        "cool" -> gn.magic.cool
      ))

      val values = Json.obj(
        "can-grade" -> jam.node.head.asInstanceOf[EventNode].meta.get("can-grade"),
        "username" -> username,
        "jam_id" -> jams.node_id,
        "user_ud" -> user.node_id,
        "user_games_reduced" -> Json.toJson(userGamesFiltered),
        // get games with matching jam node_id
        "user_games" -> Json.toJson(userGames.filter(_.parent == jams.node_id).sortBy(_.published)),
        "games" -> Json.toJson(games),
      )
      Ok(values)
    }

  }
}