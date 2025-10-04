package controllers.ld58

import controllers.*
import io.circe
import io.circe.Json
import io.circe.syntax.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.libs.circe.Circe
import play.api.libs.ws.WSClient
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import service.ld58.{GameInfo, Ld58Service, UrlSigner}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

private val logger = Logger(classOf[Ld58Controller])

class Ld58Controller(cc: ControllerComponents,
                     ld58: Ld58Service,
                     urlSigner: UrlSigner,
                     wsClient: WSClient,
                     implicit val ec: ExecutionContext)
                    (implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) with Circe {


  def stats() = Action.async { request =>
    logger.info(s"${request.connection.remoteAddress} requested stats")
    Future.successful(Ok("OK".asJson))
  }

  def gamesFromJam(jam: String): Action[AnyContent] = Action.async { implicit req =>
    for (
      (jamState, games) <- ld58.gamesFromJam(jam)
    ) yield {
      Ok(Json.obj(
        "can-grade" -> jamState.canGrade.asJson,
        "games" -> games.asJson
      ))
    }
  }

  def gamesFromUser(jam: String, username: String): Action[AnyContent] = Action.async { implicit req =>
    for (
      (currentGame, games) <- ld58.gameFromUser(jam, username)
    ) yield {
      Ok(Json.obj(
        "current" -> currentGame.asJson,
        "games" -> games.asJson
      ))
    }
  }


  def gamesHexGrid(jam: String): Action[AnyContent] = Action.async {
    for (
      hexgrid <- ld58.hexGridFromJam(jam)
    ) yield {
      Ok(hexgrid.asJson)
    }
  }

  def persistGameOnGrid(q: Int, r: Int, gameId: Int): Action[AnyContent] = Action.async {
    for (
      ret <- ld58.persistGameOnGrid(q, r, gameId)
    ) yield {
      if (ret == gameId) {
        Ok(ret.asJson)
      } else {
        BadRequest(ret.asJson)
      }
    }
  }

  def proxyImage(signedUrl: String) = Action.async {
    urlSigner.verifySigned(signedUrl) match {
      case Some(trustedUrl) =>
        wsClient.url(trustedUrl).get() map { response =>
          logger.warn(s"Content type: ${response.contentType}")
          Ok.chunked(response.bodyAsSource, Option(response.contentType))
        }
      case None => Future.successful(Forbidden)
    }
  }

}