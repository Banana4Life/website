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

final case class JoinRequestMessage(id: UUID, offer: String)
object JoinRequestMessage {
  implicit val format: Format[JoinRequestMessage] = Json.format
}

final case class JoinAcceptMessage(peerId: Int, answer: String)
object JoinAcceptMessage {
  implicit val format: Format[JoinAcceptMessage] = Json.format
}

sealed trait HosterMessage
final case class HostingMessage(playerCount: Int) extends HosterMessage
final case class HostAcceptsJoinMessage(id: UUID, peerId: Option[Int], answer: String) extends HosterMessage


object HosterMessage {
  implicit val hostingFormat: Format[HostingMessage] = Json.format
  implicit val hostAcceptsJoinMessageFormat: Format[HostAcceptsJoinMessage] = Json.format
  implicit val hosterMessageFormat: Format[HosterMessage] = Json.format
}

sealed trait JoinerMessage
final case class JoinMessage(offer: String) extends JoinerMessage

object JoinerMessage {
  implicit val joinFormat: Format[JoinMessage] = Json.format
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
                  id: UUID,
                  remote: InetAddress,
                  private val hosts: ConcurrentMap[UUID, GameHost]) extends SignalActor(out, id, remote, hosters) {
  override def receiveText(text: String) = {
    try
      Json.parse(text).as[HosterMessage] match
        case HostingMessage(playerCount) =>
          hosts.put(id, GameHost(id, playerCount, Instant.now()))
        case controllers.HostAcceptsJoinMessage(id, peerId, answer) =>
          val joiner = joiners.get(id)
          if (joiner != null) {
            joiner ! Json.toJson(JoinAcceptMessage(peerId.getOrElse(2), answer)).toString
          }
    catch
      case e: Exception =>
        logger.error("Kaputt", e)
  }

  override def postStop(): Unit = {
    super.postStop()
    hosts.remove(id)
  }
}

class JoinHandler(out: ActorRef,
                  private val hosters: ConcurrentMap[UUID, ActorRef],
                  joiners: ConcurrentMap[UUID, ActorRef],
                  id: UUID,
                  remote: InetAddress,
                  private val hosts: ConcurrentMap[UUID, GameHost]) extends SignalActor(out, id, remote, joiners) {
  override def receiveText(text: String) = {
    logger.info(Json.toJson[JoinerMessage](JoinMessage("SOME OFFER")).toString)
    Json.parse(text).as[JoinerMessage] match
      case JoinMessage(offer) =>
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
          hosters.get(latestHost.id) ! Json.toJson(JoinRequestMessage(id, offer)).toString
        } else {
          logger.warn("No host available!")
        }
  }
}