package service

import akka.actor.ActorSystem
import akka.stream.Materializer
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object HealthCheck {
    def main(args: Array[String]): Unit = {
        val actorSystem = ActorSystem.create("healthcheck")
        implicit val materializer = Materializer.matFromSystem(actorSystem)
        val client = AhcWSClient()

        try {
            val result = Await.result(client.url("http://localhost:9000").withRequestTimeout(Duration.Inf).get(), Duration.Inf)
            println(result.status)
            if ((200 until 300).contains(result.status)) System.exit(0)
            else System.exit(1)
        } catch {
            case e: Exception =>
                e.printStackTrace(System.err)
                System.exit(1)
        }
    }
}
