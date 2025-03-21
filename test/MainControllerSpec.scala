import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
@RunWith(classOf[JUnitRunner])
class MainControllerSpec extends Specification {

    "Application" should {

        "send 404 on a bad request" in new WithApplication {
            route(app, FakeRequest(GET, "/boum")) must beNone
        }

        "render the index page" in new WithApplication {
            val home = route(app, FakeRequest(GET, "/")).get

            status(home) must equalTo(OK)
            contentType(home) must beSome[String].which(_ == "text/html")
            contentAsString(home) must contain("Banana4Life")
        }
    }
}
