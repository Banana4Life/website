package controllers.ld58

import controllers.*
import io.circe
import io.circe.Json
import io.circe.syntax.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import service.ld58.{GameInfo, Ld58Service}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

private val logger = Logger(classOf[Ld58Controller])


class Ld58Controller(cc: ControllerComponents,
                     ld58: Ld58Service,
                     implicit val ec: ExecutionContext)
                    (implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) with Circe {


  def stats() = Action.async { request =>
    logger.info(s"${request.connection.remoteAddress} requested stats")
    Future.successful(Ok("OK".asJson))
  }

  def gamesFromJam(jam: String): Action[AnyContent] = Action.async {
    for (
      (jamState, games) <- ld58.gamesFromJam(jam)
    ) yield {
      Ok(Json.obj(
        "can-grade" -> jamState.canGrade.asJson,
        "games" -> games.asJson
      ))
    }
  }


  def gameFromUser(jam: String, username: String): Action[AnyContent] = Action.async {
    for (
      games <- ld58.gameFromUser(jam, username)
    ) yield {
      Ok(games.asJson)
    }
  }
}