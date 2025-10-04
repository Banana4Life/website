package service.ld58

import io.circe.{Encoder, derivation}
import io.circe.derivation.ConfiguredEncoder
import play.api.libs.json.{JsObject, Json}
import service.{EventNode, GameNode, LdjamService}

import scala.concurrent.{ExecutionContext, Future}

given derivation.Configuration = derivation.Configuration.default

case class JamState(id: Int, canGrade: Boolean)

case class GameInfo(id: Int, name: String, cover: Option[String], web: Option[String], cool: Double) derives ConfiguredEncoder

case class User(id: Int)


class Ld58Service(ldjam: LdjamService, implicit val ec: ExecutionContext) {
  private val LINK_TAG_WEB = 42336

  def jamState(jam: String): Future[JamState] = {
    for {
      jams <- ldjam.walk2(1, s"/events/ludum-dare/$jam")
      jam <- ldjam.getNodes2(Seq(jams.node_id))
    } yield {
      JamState(jams.node_id, jam.node.head.asInstanceOf[EventNode].meta.get("can-grade") == "1")
    }
  }


  def gameFromUser(jam: String, username: String): Future[Seq[GameInfo]] = {
    for {
      jamState <- jamState(jam)
      user <- getUser(username)
      userGames <- ldjam.getFeedOfNodes(user.id, Seq("authors"), "item", Some("game"), None, 10, 1)
        .map(_.flatMap { case gn: GameNode => Some(gn); case _ => None })
    } yield {
      userGames.map(node => {
        gameNodeToInfo(node)
      }).filter(_ != null)
    }
  }

  private def gameNodeToInfo(node: GameNode) = {
    val web = if (node.meta.`link-01-tag`.map(_.as[Seq[Int]]).getOrElse(Seq.empty).contains(LINK_TAG_WEB)) node.meta.`link-01`
    else if (node.meta.`link-02-tag`.map(_.as[Seq[Int]]).getOrElse(Seq.empty).contains(LINK_TAG_WEB)) node.meta.`link-02` else None
    val coverUrl = node.meta.cover.map(cover => ldjam.cdnUrl(cover.replace("///", "/") + ".480x384.fit.jpg"))

    GameInfo(node.id, node.name, coverUrl, web, node.magic.cool)
  }

  private def getUser(username: String) = {
    ldjam.walk2(1, s"/users/$username").map(u => {
      User(u.node_id)
    })
  }

  def gamesFromJam(jam: String): Future[(JamState, Seq[GameInfo])] = {
    for {
      jamState <- jamState(jam)
      games <- ldjam.getFeedOfNodes(jamState.id, Seq("parent"), "item", Some("game"), Some("compo+jam+extra"), 10, 10)
    } yield {
      val filtered = games.flatMap {
        case gn: GameNode => Some(gn)
//        case gn: GameNode if gn.meta.cover.nonEmpty => Some(gn)
        case _ => None
      }.map(gn => gameNodeToInfo(gn))

      (jamState, filtered)
    }
  }
}
