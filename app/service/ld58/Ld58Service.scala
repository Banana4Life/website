package service.ld58

import io.circe.derivation.{ConfiguredCodec, ConfiguredEncoder}
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, derivation, parser}
import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.mvc.RequestHeader
import service.{EventNode, GameNode, LdjamService}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

given derivation.Configuration = derivation.Configuration.default

private val logger = Logger(classOf[Ld58Service])

case class JamState(id: Int,
                    canGrade: Boolean,
                    published: Int,
                    signups: Int,
                    authors: Int,
                    unpublished: Int
                   ) derives ConfiguredCodec

case class GameInfo(id: Int, jamId: Int, name: String, cover: Option[String], web: Option[String], cool: Double, path: String) derives ConfiguredCodec

case class User(id: Int)


case class Award(key: String, icon: String, name: String) derives ConfiguredCodec {

}
object Award {
  val AWARDS = Seq(
    Award(key = "sleep", icon = "💤", name = "Probably did not sleep"),
    Award(key = "caffeine", icon = "☕", name = "Fueled by Caffeine"),
    Award(key = "surprise", icon = "🔥", name = "Surprised it runs"),
    Award(key = "bug", icon = "🐛", name = "Look, a Bug"),
    Award(key = "collision", icon = "🧱", name = "Collisions"),
    Award(key = "rocket", icon = "🚀", name = "Rocket Science"),
    Award(key = "yesterday", icon = "🫠", name = "It Worked Yesterday"),
    Award(key = "gitgud", icon = "💀", name = "Git Gud"),
    Award(key = "easy", icon = "👶🏻", name = "Easy gg"),
    Award(key = "onemoretime", icon = "🔄", name = "Just one more time"),
    Award(key = "grass", icon = "🌿", name = "Touch some grass"),
    Award(key = "chefkiss", icon = "👨🏽‍🍳", name = "Chef's kiss"),
  )
}

case class GivenAward(key: String, byUser: String) derives ConfiguredCodec {

}

case class GivenRating(rating: Int, byUser: String) derives ConfiguredCodec {

}

class Ld58Service(ldjam: LdjamService,
                  persistence: Ld58PersistenceService,
                  cache: AsyncCacheApi,
                  urlSigner: UrlSigner,
                  implicit val ec: ExecutionContext) {
  private val LINK_TAG_WEB = 42336

  private def memCached[T: ClassTag](key: String)(future: => Future[T], duration: Duration = 1.minutes): Future[T] = {
    cache.getOrElseUpdate(key) {
      future.flatMap(v => cache.set(key, v, duration).map(_ => v))
    }
  }

  def jamState(jam: String): Future[JamState] = {
    for {
      jamId <- fetchJamId(jam)
      jamNode <- fetchNode[EventNode](jamId)
      stats <- fetchJamStats(jamId)
    } yield {
      JamState(jamId,
        jamNode.meta.get("can-grade") == "1",
        stats.stats.jam + stats.stats.compo + stats.stats.extra,
        stats.stats.signups,
        stats.stats.authors,
        stats.stats.unpublished
      )
    }
  }

  private def fetchJamStats(jamId: Int) = memCached(s"jamStats.$jamId") {
    ldjam.stats(jamId)
  }

  private def fetchJamId(jam: String) = memCached(s"jamState.$jam") {
    ldjam.walk2(1, s"/events/ludum-dare/$jam").map(_.node_id)
  }

  private def fetchNode[T: ClassTag](nodeId: Int): Future[T] = memCached(s"node.${nodeId}") {
    ldjam.getNodes2(Seq(nodeId)).map(_.head.asInstanceOf[T])
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

    GameInfo(node.id, node.parent, node.name, coverUrl.map(urlSigner.proxiedUrl), webUrl, node.magic.map(_.cool).getOrElse(0), node.path)
  }

  private def fetchGameNodeFeed(jamId: Int,
                             knownGames: Seq[Int],
                             methods: Seq[String],
                             sliceSize: Int,
                            ): Future[Seq[Int]] = {
    def getSlide(offset: Int, nodeIds: Seq[Int]): Future[Seq[Int]] = {
      ldjam.getNodeFeed(jamId, methods, "item", Some("game"), Some("compo+jam+extra"), offset, sliceSize).flatMap { response =>
        if (response.feed.isEmpty) Future.successful(nodeIds)
        else {
          getSlide(offset + sliceSize, nodeIds ++ response.feed.map(_.id))
        }
      }
    }

    getSlide(0, Vector.empty)
  }

  private def findWebUrl(node: GameNode): Option[String] = {
    val links = Seq(
      (node.meta.`link-01-tag`, node.meta.`link-01`),
      (node.meta.`link-02-tag`, node.meta.`link-02`),
      (node.meta.`link-03-tag`, node.meta.`link-03`),
      (node.meta.`link-04-tag`, node.meta.`link-04`),
      (node.meta.`link-05-tag`, node.meta.`link-05`),
    );
    // -tag are arrays of int
    links.find(tuple => tuple._1.flatMap(_.as[Seq[Int]].toOption).getOrElse(Seq.empty).contains(LINK_TAG_WEB)).flatMap(_._2)
  }

  private def fetchGameNodesByIdsLimited(nodeIds: Seq[Int], sliceSize: Int, maxLimit: Int, predicate: GameNode => Boolean): Future[Seq[GameNode]] = {
    if (nodeIds.isEmpty) {
      return Future.successful(Seq.empty)
    }
//    logger.info("fetchGameNodesByIdsLimited " + nodeIds.length)

    def getSlide(offset: Int, nodes: Seq[GameNode]): Future[Seq[GameNode]] = {
      val sliceToFetch = nodeIds.slice(offset, offset + sliceSize)


      if (sliceToFetch.isEmpty) {
        return Future.successful(nodes)
      }

      val newNodes = Future.sequence(sliceToFetch.map(nodeId => {
//        logger.info("cache" + s"node.$nodeId")
          cache.get[GameNode](s"node.$nodeId")
        })).map(_.flatten)
        .flatMap(cached => {
          val notCachedIds = sliceToFetch.filterNot(cached.map(_.id).contains)
          if (notCachedIds.isEmpty) {
            Future.successful(cached)
          } else {
            ldjam.getNodes2(notCachedIds).map(resp => {
              resp.collect {
                case gn: GameNode => gn
              }
            }).map(notCached => {
              notCached.foreach(node => Future(cache.set(s"node.${node.id}", node)))
              cached ++ notCached
            })
          }
        })

      newNodes.flatMap(nn => {
        val totalNodes = nodes ++ nn.filter(predicate)
        if (totalNodes.size >= maxLimit) Future.successful(totalNodes)
        else getSlide(offset + sliceSize, totalNodes)
      })
    }

    getSlide(0, Vector.empty)
  }

  private def fetchGameNodesByIds(nodeIds: Seq[Int]): Future[Seq[GameNode]] = {
    val chunks = nodeIds.grouped(200).toSeq
    val nodesFutures = Future.sequence(chunks.map(ids => ldjam.getNodes2(ids))).map(_.flatten)
    nodesFutures.map(_ collect {
      case gn: GameNode => gn
    })
  }

  private def fetchGamesFromJam(jamId: Int)(implicit req: RequestHeader): Future[List[GameInfo]] = memCached(s"games.$jamId") {
    for {
      hexGrid <- persistence.hGetAllInt(hexGridCacheKey(jamId))
      knownGames <- fetchGameNodesByIds(hexGrid.values.toSeq) // TODO nothing works when persistence is unavailable
      allGameIds <- fetchGameNodeFeed(jamId, knownGames.map(_.id), Seq("cool", "parent"), 200)
      webGames <- fetchGameNodesByIdsLimited(allGameIds.filterNot(knownGames.map(_.id).contains), 200, 50,
                        n => n.meta.cover.isDefined && findWebUrl(n).isDefined)
      nonWebGames <- fetchGameNodesByIdsLimited(allGameIds.filterNot(knownGames.map(_.id).contains)
                                                          .filterNot(webGames.map(_.id).contains), 200, 50,
                        n => n.meta.cover.isDefined)
    }
    yield {
      val allGames = knownGames ++ webGames ++ nonWebGames

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
      jamId <- fetchJamId(jam)
      hexGrid <- persistence.hGetAllInt(hexGridCacheKey(jamId))
    } yield {
      hexGrid
    }
  }

  def givenAwards(jam: String): Future[Map[Int, List[GivenAward]]] = {

    for {
      jamId <- fetchJamId(jam)
      awards <- persistence.hGetAll(awardsCacheKey(jamId))
    } yield {
      awards.map { case (key, value) => (key.toInt, parser.parse(value).flatMap(_.as[List[GivenAward]]).toOption.getOrElse(List.empty)) }
    }
  }

  def giveAward(gameId: Int, user: String, awardKey: String): Future[Boolean] = {
    val found = Award.AWARDS.find(_.key == awardKey)
    if (found.isEmpty) {
      return Future.successful(false)
    }
    if (user == "Guest") {
      return Future.successful(false)
    }

    for {
      userObj <- fetchUser(user)
      game <- fetchNode[GameNode](gameId)
      userGames <- fetchGamesOfUser(userObj.id)
      prevAwardsRaw <- persistence.hGet(awardsCacheKey(game.parent), gameId.toString)
      prevAwards = prevAwardsRaw.flatMap(parser.parse(_).toOption)
                                .flatMap(_.as[List[GivenAward]].toOption)
                                .getOrElse(Seq.empty)
      awards <- persistAwards(userGames.map(_.id), gameId, user, awardKey, game, prevAwards)
    } yield {
      awards
    }
  }

  private def persistAwards(ownGames: Seq[Int], gameId: Int, user: String, awardKey: String, game: GameNode, prevAwards: Seq[GivenAward]) = {
    if (ownGames.contains(gameId))
      Future.successful(false)
    else
      persistence.hSet(awardsCacheKey(game.parent), gameId.toString, (prevAwards ++ Seq(GivenAward(awardKey, user))).distinct.asJson.noSpaces).map(_ => true)
  }

  def userRatings(jam: String, user: String): Future[Map[Int, Int]] = {
    for {
      jamId <- fetchJamId(jam)
      ratings <- persistence.hGetAll(ratingsCacheKey(jamId))
    } yield {
      val allRatings = ratings.map { case (key, value) => (key.toInt, parser.parse(value).flatMap(_.as[List[GivenRating]]).toOption.getOrElse(List.empty)) }
      allRatings.view.mapValues(_.find(a => a.byUser == user)).collect {
        case (k, Some(v)) => (k, v.rating)
      }.toMap
    }
  }

  def gameRating(gameId: Int): Future[Int] = {
    for {
      gameNode <- fetchNode[GameNode](gameId)
      ratings <- persistence.hGet(ratingsCacheKey(gameNode.parent), gameId.toString)
    } yield {
      val givenRatings = ratings.flatMap(parser.parse(_).toOption)
        .flatMap(_.as[List[GivenRating]].toOption)
        .getOrElse(Seq.empty)
      if (givenRatings.isEmpty)
        0
      else
        (givenRatings.map(_.rating).sum.toFloat / givenRatings.length).round
    }
  }

  def giveRating(gameId: Int, user: String, rating: Int): Future[Boolean] = {
    for {
      game <- fetchNode[GameNode](gameId)
      prevRatingsRaw <- persistence.hGet(ratingsCacheKey(game.parent), gameId.toString)
      prevRatings = prevRatingsRaw.flatMap(parser.parse(_).toOption)
        .flatMap(_.as[List[GivenRating]].toOption)
        .getOrElse(Seq.empty)
      rating <- persistence.hSet(ratingsCacheKey(game.parent), gameId.toString, (prevRatings ++ Seq(GivenRating(rating, user))).distinct.asJson.noSpaces)
    } yield {
      true
    }
  }

  def persistGameOnGrid(q: Int, r: Int, gameId: Int): Future[Int] = {
    val coord = s"$q:$r"
    for {
      gameNode <- fetchNode[GameNode](gameId)
      existing <- persistence.hGetInt(hexGridCacheKey(gameNode.parent), coord)
      hexGrid <- persistence.hGetAllInt(hexGridCacheKey(gameNode.parent))
      set <- existing.map(Future.successful).getOrElse(
          if (hexGrid.values.exists(_ == gameId))
            Future.failed(new Exception("Game already on grid"))
          else
            persistence.hSetInt(hexGridCacheKey(gameNode.parent), coord, gameId))
    } yield {

      if (gameId == set) {
        cache.remove("games")
      }
      set
    }
  }

  private def hexGridCacheKey(jamId: Int) = "hexgrid." + jamId
  private def awardsCacheKey(jamId: Int) = "awards." + jamId
  private def ratingsCacheKey(jamId: Int) = "ratings." + jamId
}
