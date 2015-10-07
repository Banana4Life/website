import java.util.Locale

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent.Future

object Global extends WithFilters(WhitespaceFilter) {
  override def onHandlerNotFound(request: RequestHeader) = Future.successful(NotFound("OH NO! -> " + request.uri))
  override def onStart(app: Application): Unit = {
    super.onStart(app)
    Locale.setDefault(Locale.ENGLISH)
  }
}
