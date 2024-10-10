package controllers

import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.libs.json.*
import play.api.libs.streams.ActorFlow
import play.api.mvc.{AbstractController, ControllerComponents, WebSocket}

import java.awt.{Color, Graphics2D}
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.time.{Duration, Instant}
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import javax.imageio.ImageIO
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.math.Ordering.Implicits.infixOrderingOps

private val logger = Logger(classOf[Ld56C2Controller])

final case class JoiningMessage(id: UUID)
object JoiningMessage {
  implicit val format: Format[JoiningMessage] = Json.format
}

final case class OfferingMessage(id: UUID, offer: String)
object OfferingMessage {
  implicit val format: Format[OfferingMessage] = Json.format
}

final case class JoinAcceptMessage(id: UUID, peerId: Int)
object JoinAcceptMessage {
  implicit val format: Format[JoinAcceptMessage] = Json.format
}

final case class IceCandidateMessage(sourceId: UUID, destinationId: UUID, m: String, i: Int, name: String) extends HosterMessage with JoinerMessage
object IceCandidateMessage {
  implicit val format: Format[IceCandidateMessage] = Json.format
}

final case class AnswerMessage(answer: String)
object AnswerMessage {
  implicit val format: Format[AnswerMessage] = Json.format
}

sealed trait HosterMessage
final case class HostingMessage(playerCount: Int) extends HosterMessage
final case class  HostAcceptsJoinMessage(id: UUID, peerId: Int) extends HosterMessage
final case class AnsweringMessage(destination: UUID, answer: String) extends HosterMessage


object HosterMessage {
  implicit val hostingFormat: Format[HostingMessage] = Json.format
  implicit val hostAcceptsJoinMessageFormat: Format[HostAcceptsJoinMessage] = Json.format
  implicit val answeringMessageFormat: Format[AnsweringMessage] = Json.format
  implicit val hosterMessageFormat: Format[HosterMessage] = Json.format
}

sealed trait JoinerMessage
final case class JoinMessage() extends JoinerMessage
final case class OfferMessage(destination: UUID, offer: String) extends JoinerMessage

object JoinerMessage {
  implicit val joinFormat: Format[JoinMessage] = Json.format
  implicit val offerFormat: Format[OfferMessage] = Json.format
  implicit val joinerFormat: Format[JoinerMessage] = Json.format
}

final case class StatsResponse(servers: Int, latestServer: Option[Instant], players: Int, latestPlayer: Option[Instant])
object StatsResponse {
  implicit val format: Format[StatsResponse] = Json.format
}

val HostTimeout = Duration.ofSeconds(30)
final case class GameHost(id: UUID, playerCount: Int, lastUpdated: Instant, inceptionTime: Instant) {
  def isStale(asOf: Instant) = lastUpdated < asOf.minus(HostTimeout)
}

class Ld56C2Controller(cc: ControllerComponents)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {
  private val hosterConnections = ConcurrentHashMap[UUID, ActorRef]()
  private val joinerConnections = ConcurrentHashMap[UUID, ActorRef]()
  private val hosts = ConcurrentHashMap[UUID, GameHost]()
  private var lastJoinTime = AtomicReference[Instant]()

  private def gatherStats(): StatsResponse = {
    val now = Instant.now()
    hosts.entrySet().removeIf(entry => entry.getValue.isStale(now))
    val gameHosts = hosts.values().asScala.toVector
    val latestHost =
      if (hosts.isEmpty) None
      else Some(gameHosts.map(_.inceptionTime).max)
    StatsResponse(gameHosts.length, latestHost, gameHosts.map(_.playerCount).sum, Option(lastJoinTime.get()))
  }

  def stats() = Action.async { request =>
    logger.info(s"${request.connection.remoteAddress} requested stats")
    Future.successful(Ok(Json.toJson(gatherStats())))
  }

  def statsPicture() = Action.async { request =>
    logger.info(s"${request.connection.remoteAddress} requested stats as image")
    val stats = gatherStats()

    val w = 100
    val h = 50
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.getGraphics.asInstanceOf[Graphics2D]
    graphics.setColor(Color.BLACK)
    graphics.drawString(s"Servers: ${stats.servers}", 5, 15)
    graphics.drawString(s"Players: ${stats.players}", 5, 35)
    graphics.dispose()
    val output = ByteArrayOutputStream()
    ImageIO.write(image, "png", output)

    val cacheHeaders = Seq(
      "Pragma-directive" -> "no-cache",
      "Cache-directive" -> "no-cache",
      "Cache-control" -> "no-cache",
      "Pragma" -> "no-cache",
      "Expires" -> "0",
    )

    Future.successful(Ok(output.toByteArray).as("image/png").withHeaders(cacheHeaders*))
  }

  def signalHost(id: String) = WebSocket.accept[String, String] { request =>
    val clientId = UUID.fromString(id)
    ActorFlow.actorRef { out => Props(HostHandler(out, hosterConnections, joinerConnections, clientId, request.connection.remoteAddress, hosts)) }
  }

  def signalJoin(id: String) = WebSocket.accept[String, String] { request =>
    val clientId = UUID.fromString(id)
    lastJoinTime.set(Instant.now())
    ActorFlow.actorRef { out => Props(JoinHandler(out, hosterConnections, joinerConnections, clientId, request.connection.remoteAddress, hosts)) }
  }
}

abstract class SignalActor(val out: ActorRef, val id: UUID, val remote: InetAddress, private val connections: ConcurrentMap[UUID, ActorRef]) extends Actor {

  override def preStart(): Unit = {
    logger.info(s"$id - $remote - Connected!")
    connections.put(id, out)
  }

  final def receive = {
    case msg: String =>
      logger.info(s"$id - $remote - Received: $msg")
      receiveText(msg)
  }

  def receiveText(text: String): Unit

  override def postStop() = {
    logger.info(s"$id - $remote - Connection closed!")
    connections.remove(id)
  }
}

class HostHandler(out: ActorRef,
                  hosters: ConcurrentMap[UUID, ActorRef],
                  private val joiners: ConcurrentMap[UUID, ActorRef],
                  myId: UUID,
                  remote: InetAddress,
                  private val hosts: ConcurrentMap[UUID, GameHost]) extends SignalActor(out, myId, remote, hosters) {
  override def receiveText(text: String): Unit = {
    try
      Json.parse(text).as[HosterMessage] match
        case HostingMessage(playerCount) =>
          hosts.compute(myId, { (key, value) =>
            val now = Instant.now()
            if (value == null) GameHost(myId, playerCount, now, now)
            else value.copy(playerCount = playerCount, lastUpdated = now)
          })
        case HostAcceptsJoinMessage(id, peerId) =>
          val joiner = joiners.get(id)
          if (joiner != null) {
            joiner ! Json.toJson(JoinAcceptMessage(myId, peerId)).toString
          }
        case candidate @ IceCandidateMessage(_, dest, _, _, _) =>
          joiners.get(dest) ! Json.toJson(candidate).toString
        case AnsweringMessage(dest, answer) =>
          joiners.get(dest) ! Json.toJson(AnswerMessage(answer)).toString
    catch
      case e: Exception =>
        logger.error("Kaputt", e)
  }

  override def postStop(): Unit = {
    super.postStop()
    hosts.remove(myId)
  }
}

class JoinHandler(out: ActorRef,
                  private val hosters: ConcurrentMap[UUID, ActorRef],
                  joiners: ConcurrentMap[UUID, ActorRef],
                  myId: UUID,
                  remote: InetAddress,
                  private val hosts: ConcurrentMap[UUID, GameHost]) extends SignalActor(out, myId, remote, joiners) {
  override def receiveText(text: String): Unit = {
    try
      Json.parse(text).as[JoinerMessage] match
        case JoinMessage() =>
          val it = hosts.entrySet().iterator()
          val viableHosts = ArrayBuffer[GameHost]()
          val now = Instant.now()
          while (it.hasNext) {
            val entry = it.next()
            val host = entry.getValue
            if (host.isStale(now)) {
              val actor = hosters.remove(entry.getKey)
              it.remove()
              actor ! PoisonPill
            } else {
                viableHosts.append(host)
            }
          }
          if (viableHosts.nonEmpty) {
            for { host <- pickBestHost(viableHosts.toIndexedSeq) } {
              hosters.get(host.id) ! Json.toJson(JoiningMessage(myId)).toString
            }
          }
        case OfferMessage(dest, offer) =>
          hosters.get(dest) ! Json.toJson(OfferingMessage(myId, offer)).toString
        case candidate @ IceCandidateMessage(_, dest, _, _, _) =>
          hosters.get(dest) ! Json.toJson(candidate).toString
    catch
      case e: Exception =>
        logger.error("Kaputt", e)
  }

  private def pickBestHost(hosts: IndexedSeq[GameHost]): Option[GameHost] = {
    val actuallyViable = hosts.filter(_.playerCount < 30)
    val playerCountOrdering: Ordering[GameHost] = Ordering.by(_.playerCount)
    val inceptionTimeOrdering: Ordering[GameHost] = Ordering.by(_.inceptionTime)
    actuallyViable.sorted(playerCountOrdering.reverse orElse inceptionTimeOrdering.reverse).headOption
  }
}
