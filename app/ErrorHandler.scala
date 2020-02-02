import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import play.api.routing.Router
import play.api.{Configuration, Environment}
import play.core.SourceMapper

import scala.concurrent.Future

class ErrorHandler(env: Environment, config: Configuration, sourceMapper: Option[SourceMapper], router: => Option[Router])
    extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

    override protected def onNotFound(request: RequestHeader, message: String): Future[Result] = {
        Future.successful(NotFound(views.html.error.render("Cannot find: " + request.uri)))
    }
}
