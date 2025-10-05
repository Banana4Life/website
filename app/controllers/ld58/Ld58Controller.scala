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
import service.ld58.Award.AWARDS
import service.ld58.{Award, GameInfo, Ld58Service, UrlSigner}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

private val logger = Logger(classOf[Ld58Controller])

class Ld58Controller(cc: ControllerComponents,
                     ld58: Ld58Service,
                     urlSigner: UrlSigner,
                     wsClient: WSClient,
                     implicit val ec: ExecutionContext)
                    (implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) with Circe {


  def stats(jam: String = "56") = Action.async { request =>
    logger.info(s"${request.connection.remoteAddress} requested stats")
    for (
      stats <- ld58.jamState(jam)
    ) yield {
      Ok(stats.asJson)
    }
  }

  def gamesFromJam(jam: String): Action[AnyContent] = Action.async { implicit req =>
    for (
      (jamState, games) <- ld58.gamesFromJam(jam)
    ) yield {
      Ok(games.asJson)
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
      ret <- ld58.persistGameOnGrid(q, r, gameId) recover { case e => 0 }
    ) yield {
      if (ret == 0) Conflict(ret.asJson)
      else if (ret == gameId) Ok(ret.asJson)
      else BadRequest(ret.asJson)
    }
  }

  def proxyImage(signedUrl: String, cacheKey: Option[String]) = Action.async {
    urlSigner.verifySigned(signedUrl) match {
      case Some(trustedUrl) =>
        wsClient.url(trustedUrl).get() map { response =>
          val cacheHeaders =
            if (cacheKey.isEmpty) Seq.empty
            else Seq(
              ("Cache-Control", "public, max-age=604800, immutable, stale-while-revalidate=86400")
            )
          Ok.chunked(response.bodyAsSource, Option(response.contentType))
            .withHeaders(cacheHeaders*)
        }
      case None => Future.successful(Forbidden)
    }
  }
  
  def awards(): Action[AnyContent] = Action {
    Ok(AWARDS.asJson)
  }

  def givenAwards(jam: String): Action[AnyContent] = Action.async {
    ld58.givenAwards(jam).map { awards =>
      Ok(awards.asJson)
    }
  }

  def giveAward(gameId: Int, user: String, awardKey: String): Action[AnyContent] = Action.async {
    ld58.giveAward(gameId, user, awardKey).map { r =>
      Ok(r.asJson)
    }
  }


}