package controllers.ld58

import controllers.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.libs.json.*
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.Future

private val logger = Logger(classOf[Ld58Controller])

class Ld58Controller(cc: ControllerComponents)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {
  def stats() = Action.async { request =>
    logger.info(s"${request.connection.remoteAddress} requested stats")
    Future.successful(Ok(Json.toJson("OK")))
  }
}