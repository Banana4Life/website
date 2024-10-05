package service

import play.api.Logger
import play.api.libs.json.*

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.Random

sealed trait ResponseMessage
final case class HostResponseMessage(host: String, port: Int) extends ResponseMessage
final case class JoinResponseMessage(host: String, port: Int, playerCount: Int) extends ResponseMessage
final case class PunchRequestMessage(host: String, port: Int) extends ResponseMessage

object ResponseMessage {
  implicit val hostResponseFormat: Format[HostResponseMessage] = Json.format
  implicit val joinResponseFormat: Format[JoinResponseMessage] = Json.format
  implicit val punchRequestFormat: Format[PunchRequestMessage] = Json.format
}

sealed trait Message
final case class HostMessage() extends Message
final case class HostingMessage(host: String, port: Int, playerCount: Int) extends Message
final case class JoinMessage() extends Message

object Message {
  implicit val hostFormat: Format[HostMessage] = Json.format
  implicit val hostingFormat: Format[HostingMessage] = Json.format
  implicit val joinFormat: Format[JoinMessage] = Json.format
  implicit object MessageFormat extends Format[Message] {
    override def reads(json: JsValue): JsResult[Message] = json match
      case JsObject(obj) =>
        obj.get("type") match
          case Some(JsString("HOST")) => hostFormat.reads(json)
          case Some(JsString("HOSTING")) => hostingFormat.reads(json)
          case Some(JsString("JOIN")) => joinFormat.reads(json)
          case t => JsError(s"Unknown type: $t")
      case _ => JsError("Messages must be string!")

    override def writes(o: Message): JsValue = o match
      case m: HostMessage => hostFormat.writes(m)
      case m: HostingMessage => hostingFormat.writes(m)
      case m: JoinMessage => joinFormat.writes(m)
  }
}

final case class Host(addr: String, port: Int)

object Host {
  implicit val format: Format[Host] = Json.format
}

final case class HostingHost(gameHost: Host, c2Host: Host, playerCount: Int, lastHosted: Instant)

class Ld56MasterServer {
  private val logger = Logger(classOf[Ld56MasterServer])
  private val channel = DatagramChannel.open()
  channel.bind(InetSocketAddress(39875))
  private val readBuffer = ByteBuffer.allocateDirect(8196)
  private val writeBuffer = ByteBuffer.allocateDirect(8196)
  private val hostMap = ConcurrentHashMap[String, HostingHost]()
  logger.info("LD56 C2 Server listening!")

  private val ioThread = Thread.ofVirtual().start(() => {
    while (!Thread.interrupted()) {
      readBuffer.clear()
      val sourceAddress = channel.receive(readBuffer).asInstanceOf[InetSocketAddress]
      readBuffer.flip()
      if (readBuffer.remaining() > 0) {
        val stringContent = StandardCharsets.UTF_8.decode(readBuffer).toString
        try
          val message = Json.parse(stringContent).as[Message]
          processMessage(message, sourceAddress)
        catch
          case e: Exception =>
            logger.error(s"Invalid message: $stringContent", e)
      }
    }
  })

  CacheHelper

  private def sendMessage[T <: ResponseMessage](message: T, to: SocketAddress)(implicit writes: Writes[T]): Unit = {
    logger.info(s"Sending message to $to: $message")
    val responseBytes = Json.toBytes(Json.toJson(message))
    channel.send(ByteBuffer.wrap(responseBytes), to)
  }

  private def processMessage(message: Message, sourceAddr: InetSocketAddress): Unit = {
    logger.info(s"Received message from $sourceAddr: $message")
    val now = Instant.now()
    message match
      case HostMessage() =>
        sendMessage(HostResponseMessage(sourceAddr.getAddress.getHostAddress, sourceAddr.getPort), sourceAddr)
      case HostingMessage(host, port, playerCount) =>
        val hostPort = s"$host:$port"
        hostMap.put(hostPort, HostingHost(Host(host, port), Host(sourceAddr.getAddress.getHostAddress, sourceAddr.getPort), playerCount, now))
      case JoinMessage() =>
        hostMap.entrySet().removeIf(entry => {
          entry.getValue.lastHosted < now.minus(Duration.ofSeconds(20))
        })
        val hosts = hostMap.values().asScala.toVector
        if hosts.nonEmpty then
          val randomHost = hosts(Random.nextInt(hosts.length))

          sendMessage(PunchRequestMessage(sourceAddr.getAddress.getHostAddress, sourceAddr.getPort), InetSocketAddress.createUnresolved(randomHost.c2Host.addr, randomHost.c2Host.port))
          sendMessage(JoinResponseMessage(randomHost.gameHost.addr, randomHost.gameHost.port, randomHost.playerCount), sourceAddr)
  }
}
