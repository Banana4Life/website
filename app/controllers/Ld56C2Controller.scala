package controllers

import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsString, JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc.{ControllerComponents, WebSocket}

import java.net.InetAddress
import java.time.{Duration, Instant}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
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
final case class HostAcceptsJoinMessage(id: UUID, peerId: Int) extends HosterMessage
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

final case class GameHost(id: UUID, playerCount: Int, lastUpdated: Instant)

class Ld56C2Controller(cc: ControllerComponents)(implicit system: ActorSystem, mat: Materializer) {
  private val hosterConnections = ConcurrentHashMap[UUID, ActorRef]()
  private val joinerConnections = ConcurrentHashMap[UUID, ActorRef]()
  private val hosts = ConcurrentHashMap[UUID, GameHost]()

  def signalHost(id: String) = WebSocket.accept[String, String] { request =>
    val clientId = UUID.fromString(id)
    ActorFlow.actorRef { out => Props(HostHandler(out, hosterConnections, joinerConnections, clientId, request.connection.remoteAddress, hosts)) }
  }

  def signalJoin(id: String) = WebSocket.accept[String, String] { request =>
    val clientId = UUID.fromString(id)
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
          hosts.put(myId, GameHost(myId, playerCount, Instant.now()))
        case HostAcceptsJoinMessage(id, peerId) =>
          val joiner = joiners.get(id)
          if (joiner != null) {
            joiner ! Json.toJson(JoinAcceptMessage(myId, peerId)).toString
          }
        case candidate @ IceCandidateMessage(_, dest, _, _, _) =>
          joiners.get(dest) ! Json.toJson(candidate)
        case AnsweringMessage(dest, answer) =>
          joiners.get(dest) ! Json.toJson(AnswerMessage(answer))
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
          var latestUpdate = Instant.MIN
          var latestHost: GameHost = null
          while (it.hasNext) {
            val entry = it.next()
            val host = entry.getValue
            if (host.lastUpdated < Instant.now().minus(Duration.ofSeconds(1000))) {
              val actor = hosters.remove(entry.getKey)

              it.remove()
              actor ! PoisonPill
            } else {
              if (host.lastUpdated > latestUpdate) {
                latestUpdate = host.lastUpdated
                latestHost = host
              }
            }
          }
          if (latestHost != null) {
            hosters.get(latestHost.id) ! Json.toJson(JoiningMessage(myId)).toString
          } else {
            logger.warn("No host available!")
          }
        case OfferMessage(dest, offer) =>
          hosters.get(dest) ! Json.toJson(OfferingMessage(myId, offer))
        case candidate @ IceCandidateMessage(_, dest, _, _, _) =>
          hosters.get(dest) ! Json.toJson(candidate)
    catch
      case e: Exception =>
        logger.error("Kaputt", e)
  }
}