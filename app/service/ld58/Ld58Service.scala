package service.ld58

import io.circe.derivation.{ConfiguredCodec, ConfiguredEncoder}
import io.circe.{Encoder, derivation}
import play.api.cache.AsyncCacheApi
import service.{EventNode, GameNode, LdjamService, Node2WalkResponse}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

given derivation.Configuration = derivation.Configuration.default

case class JamState(id: Int, canGrade: Boolean)

case class GameInfo(id: Int, name: String, cover: Option[String], web: Option[String], cool: Double) derives ConfiguredCodec

case class User(id: Int)


class Ld58Service(ldjam: LdjamService,
                  persistence: Ld58PersistenceService,
                  cache: AsyncCacheApi,
                  implicit val ec: ExecutionContext) {
  private val LINK_TAG_WEB = 42336

  private def memCached[T: ClassTag](key: String)(future: => Future[T], duration: Duration = 1.minutes): Future[T] = {
    cache.get[T](key) flatMap {
      case Some(v) => {
//        println("cache hit " + key)
        Future.successful(v)
      }
      case None => {
//        println("cache miss " + key)
        future.flatMap(v => cache.set(key, v, duration).map(_ => v))
      }
    }
  }

  private def jamState(jam: String): Future[JamState] = {
    for {
      walkedJam <- fetchJam(jam)
      jamNode <- fetchJamNode(walkedJam.node_id)
    } yield {
      JamState(walkedJam.node_id, jamNode.node.head.asInstanceOf[EventNode].meta.get("can-grade") == "1")
    }
  }

  private def fetchJam(jam: String) = memCached(s"jamState.$jam") {
    ldjam.walk2(1, s"/events/ludum-dare/$jam")
  }

  private def fetchJamNode(nodeId: Int) = memCached(s"jamNode.${nodeId}") {
    ldjam.getNodes2(Seq(nodeId))
  }

  private def fetchUser(username: String) = memCached(s"userNode.$username") {
    ldjam.walk2(1, s"/users/$username").map(u => {
      User(u.node_id)
    })
  }

  private def fetchGamesOfUser(userId: Int) = memCached(s"games.byuser.$userId") {
    ldjam.getFeedOfNodes(userId, Seq("authors"), "item", Some("game"), None, 10, 1)
      .map(_.flatMap { case gn: GameNode => Some(gn); case _ => None })
  }


  def gameFromUser(jam: String, username: String): Future[Seq[GameInfo]] = {
    for {
      jamState <- jamState(jam)
      user <- fetchUser(username)
      userGames <- fetchGamesOfUser(user.id)
    } yield {
      userGames.map(mapGameNodeToInfo).filter(_ != null)
    }
  }


  private def mapGameNodeToInfo(node: GameNode): GameInfo = {
    val web = if (node.meta.`link-01-tag`.map(_.as[Seq[Int]]).getOrElse(Seq.empty).contains(LINK_TAG_WEB)) node.meta.`link-01`
    else if (node.meta.`link-02-tag`.map(_.as[Seq[Int]]).getOrElse(Seq.empty).contains(LINK_TAG_WEB)) node.meta.`link-02` else None
    val coverUrl = node.meta.cover.map(cover => ldjam.cdnUrl(cover.replace("///", "/") + ".480x384.fit.jpg"))

    GameInfo(node.id, node.name, coverUrl, web, node.magic.cool)
  }


  private def fetchGamesFromJam(jamId: Int): Future[List[GameInfo]] = {
    for {
      games <- ldjam.getFeedOfNodes(jamId, Seq("parent"), "item", Some("game"), Some("compo+jam+extra"), 200, 200)
    }
    yield {
      games.flatMap {
        //        case gn: GameNode => Some(gn)
        case gn: GameNode if gn.meta.cover.nonEmpty => Some(gn)
        case _ => None
      }.map(mapGameNodeToInfo).toList
    }
  }


  def gamesFromJam(jam: String): Future[(JamState, Seq[GameInfo])] = {
    for {
      jamState <- jamState(jam)
      gamesCached <- persistence.cached[List[GameInfo]]("games", fetchGamesFromJam(jamState.id))
    } yield {
      (jamState, gamesCached)
    }
  }
}
