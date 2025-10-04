package service.ld58

import play.api.libs.crypto.{CookieSigner, DefaultCookieSigner}
import play.api.mvc.RequestHeader

import java.nio.charset.StandardCharsets
import java.util.Base64

class UrlSigner(cookieSigner: CookieSigner) {

  private val charset = StandardCharsets.UTF_8
  private val separator = '.'
  private val base64Encoder = Base64.getUrlEncoder.withoutPadding()
  private val base64Decoder = Base64.getUrlDecoder

  def proxiedUrl(url: String)(implicit req: RequestHeader): String = {
    val signed = sign(url)
    controllers.ld58.routes.Ld58Controller.proxyImage(signed).absoluteURL()
  }

  def sign(url: String): String = {
    val base64Url = base64Encoder.encodeToString(url.getBytes(charset))
    val signature = cookieSigner.sign(base64Url)
    base64Url + separator + signature
  }

  def verifySigned(rawSignedUrl: String): Option[String] = {
    val parts = rawSignedUrl.split(separator)
    if (parts.length != 2) {
      return None
    }

    val Array(base64Url, signature) = parts
    if (signature != cookieSigner.sign(base64Url)) {
      return None
    }

    Some(String(base64Decoder.decode(base64Url), charset))
  }

}
