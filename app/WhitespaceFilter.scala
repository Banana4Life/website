import play.api.http.{MimeTypes, HeaderNames}
import play.api.Play
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.mvc.{Result, RequestHeader, Filter}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.twirl.api.{Html, Content}
import scala.concurrent.Future
import play.api.http.HeaderNames._
import play.api.Play.current

object WhitespaceFilter extends Filter {

  lazy val charset = Play.configuration.getString("default.charset").getOrElse("utf-8")
  val regex = "(\n\r?|^)\\s*(?:\n\r?|$)".r

  override def apply(next: (RequestHeader) => Future[Result])(request: RequestHeader) = next(request).flatMap(stripWhitespace)

  private def stripWhitespace(r: Result): Future[Result] = if (isHtml(r)) {
    Iteratee.flatten(r.body.apply(bodyAsString)).run.map { s =>
      val filtered = regex.replaceAllIn(s, m => m.group(1)).getBytes(charset)

      println(new String(filtered, charset))

      filtered.length -> Enumerator(filtered)
    } map {
      case (len, content) => r.copy(
        header = r.header.copy(headers = r.header.headers ++ Map(CONTENT_LENGTH -> len.toString)),
        body = Enumerator.flatten(Future.successful(content))
      )
    }
  } else Future.successful(r)

  private def isHtml(result: Result): Boolean = {
    result.header.headers.contains(HeaderNames.CONTENT_TYPE) &&
      result.header.headers.apply(HeaderNames.CONTENT_TYPE).contains(MimeTypes.HTML) &&
      manifest[Enumerator[Html]].runtimeClass.isInstance(result.body)
  }

  private def bodyAsString[A] = Iteratee.fold[A, String]("") { (str, body) =>
    body match {
      case string: String => str + string
      case template: Content => str + template.body
      case bytes: Array[Byte] => str + new String(bytes, charset)
      case _ => throw new Exception("Unexpected body: " + body)
    }
  }
}
