import javax.inject.{Provider, Inject}

import play.api.mvc.Results._
import play.api.routing.Router
import play.api.{OptionalSourceMapper, Configuration, Environment}
import play.api.http.{DefaultHttpErrorHandler, HttpErrorHandler}
import play.api.mvc.{Result, RequestHeader}

import scala.concurrent.Future

/**
  * Created by phillip on 11.03.16.
  */
class ErrorHandler @Inject() (env: Environment, config: Configuration, sourceMapper: OptionalSourceMapper, router: Provider[Router]) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {
    override protected def onNotFound(request: RequestHeader, message: String): Future[Result] = {
        Future.successful(NotFound("OH NO! -> " + request.uri))
    }
}
