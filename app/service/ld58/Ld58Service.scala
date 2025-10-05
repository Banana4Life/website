package service.ld58

import io.circe.derivation.{ConfiguredCodec, ConfiguredEncoder}
import io.circe.{Encoder, derivation}
import play.api.cache.AsyncCacheApi
import play.api.mvc.{Request, RequestHeader}
import service.{EventNode, GameNode, LdjamService, Node, Node2WalkResponse}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

given derivation.Configuration = derivation.Configuration.default

case class JamState(id: Int, canGrade: Boolean)

case class GameInfo(id: Int, jamId: Int, name: String, cover: Option[String], web: Option[String], cool: Double) derives ConfiguredCodec

case class User(id: Int)

class Ld58Service(ldjam: LdjamService,
                  persistence: Ld58PersistenceService,
                  cache: AsyncCacheApi,
                  urlSigner: UrlSigner,
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

  private def mapGameNodeToInfo(jamId: Int, node: GameNode)(implicit req: RequestHeader): GameInfo = {
    val webUrl = findWebUrl(node)
    val coverUrl = node.meta.cover.map(cover => ldjam.cdnUrl(cover.replace("///", "/") + ".480x384.fit.jpg"))

    GameInfo(node.id, node.parent, node.name, coverUrl.map(urlSigner.proxiedUrl), webUrl, node.magic.cool)
  }

  private def fetchGameNodes(jamId: Int,
                             knownGames: Seq[Int],
                             methods: Seq[String],
                             sliceSize: Int,
                             maxLimit: Int): Future[Seq[GameNode]] = {

    def getSlide(offset: Int, nodes: Seq[GameNode]): Future[Seq[GameNode]] = {
      if (nodes.size >= maxLimit) {
        return Future.successful(nodes)
      }

      ldjam.getNodeFeed(jamId, methods, "item", Some("game"), Some("compo+jam+extra"), offset, sliceSize).flatMap { response =>
        if (response.feed.isEmpty) Future.successful(nodes)
        else {
          val nodesToFetch = response.feed.map(_.id).filterNot(knownGames.contains)
          ldjam.getNodes2(nodesToFetch).flatMap { nodesResponse => {
              // find gamenodes with cover image and web game
              val gameNodes = nodesResponse.node.flatMap {
                case gn: GameNode if gn.meta.cover.nonEmpty => Some(gn)
                case _ => None
              }.filter(findWebUrl(_).isDefined)
              getSlide(offset + sliceSize, nodes ++ gameNodes)
            }
          }
        }
      }
    }

    getSlide(0, Vector.empty)
  }

  private def findWebUrl(node: GameNode) = {
    val links = Seq((node.meta.`link-01-tag`, node.meta.`link-01`),
      (node.meta.`link-02-tag`, node.meta.`link-02`),
      (node.meta.`link-03-tag`, node.meta.`link-03`),
      (node.meta.`link-04-tag`, node.meta.`link-04`),
      (node.meta.`link-05-tag`, node.meta.`link-05`),
    );
    // -tag are arrays of int
    links.find(tuple => tuple._1.map(_.as[Seq[Int]]).getOrElse(Seq.empty).contains(LINK_TAG_WEB)).flatMap(_._2)
  }

  private def fetchGameNodes(nodeIds: Seq[Int]): Future[Seq[GameNode]] = {
    val chunks = nodeIds.grouped(200).toSeq
    val nodesFutures = Future.sequence(chunks.map(ids => ldjam.getNodes2(ids)))
    nodesFutures.map(_.flatMap(_.node)).map(_ flatMap {
      case gn: GameNode => Some(gn)
      case _ => None
    })
  }

  private def fetchGamesFromJam(jamId: Int)(implicit req: RequestHeader): Future[List[GameInfo]] = memCached("games") {
    for {
      hexGrid <- persistence.hGetAllInt("hexgrid")
      knownGames <- fetchGameNodes(hexGrid.values.toSeq)
      games <- fetchGameNodes(jamId, knownGames.map(_.id), Seq("cool"), 200, 50)
    }
    yield {
      val allGames = knownGames ++ games
      allGames.toList.map(mapGameNodeToInfo(jamId, _))
    }
  }


  def gamesFromJam(jam: String)(implicit req: RequestHeader): Future[(JamState, Seq[GameInfo])] = {
    for {
      jamState <- jamState(jam)
      gamesCached <- fetchGamesFromJam(jamState.id)
    } yield {
      (jamState, gamesCached)
    }
  }

  def gameFromUser(jam: String, username: String)(implicit req: RequestHeader): Future[(Option[GameInfo], Seq[GameInfo])] = {
    for {
      jamState <- jamState(jam)
      user <- fetchUser(username)
      userGameNodes <- fetchGamesOfUser(user.id)
    } yield {
      val userGames = userGameNodes.map(mapGameNodeToInfo(jamState.id, _)).filter(_ != null)
      val currentGame = userGames.find(_.jamId == jamState.id)
      (currentGame, userGames)
    }
  }

  def hexGridFromJam(jam: String): Future[Map[String, Int]] = {
    for {
      hexGrid <- persistence.hGetAllInt("hexgrid")
    } yield {
      hexGrid
    }
  }

  def persistGameOnGrid(q: Int, r: Int, gameId: Int): Future[Int] = {
    val coord = s"$q:$r"
    for {
      existing <- persistence.hGetInt("hexgrid", coord)
      set <- existing.map(Future.successful).getOrElse(persistence.hSetInt("hexgrid", coord, gameId))
    } yield {
      set
    }
  }
}
