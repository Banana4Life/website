import java.util.Locale

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent.Future

object Global extends GlobalSettings {
  override def onStart(app: Application): Unit = {
    super.onStart(app)
    Locale.setDefault(Locale.ENGLISH)
  }
}
