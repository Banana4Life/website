package service.ld58

import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, parser}
import io.valkey.params.SetParams
import io.valkey.{JedisPool, JedisPoolConfig}
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

class Ld58PersistenceService(configuration: Configuration, implicit val ec: ExecutionContext) {
  private val config = JedisPoolConfig()
  private val logger = Logger(classOf[Ld58PersistenceService])
  private val pool = JedisPool(configuration.get[String]("ld58.persistence.uri"))

  {
    if (Using(pool.getResource) {
      jedis => {
        jedis.isConnected
      }
    }.isFailure) {
      logger.warn("ValKey is not available - No Persistance!")
    }
  }

  def set[T: Encoder](key: String, value: T, exp: Int = 60): Future[T] = Future {
    Using(pool.getResource) {
      jedis => {
        jedis.set(key, value.asJson.noSpaces, SetParams().ex(exp))
      }
    }
    value
  }


  def hSetInt(key: String, field: String, value: Int): Future[Int] = Future {
    Using(pool.getResource) {
      jedis => {
        jedis.hset(key, field, value.toString)
      }
    }
    value
  }

  def hGetInt(key: String, field: String): Future[Option[Int]] = Future {
    Using(pool.getResource) {
      jedis => jedis.hget(key, field).toInt
    }.toOption
  }

  def hClear(key: String ): Future[Unit] = Future {
    Using(pool.getResource) {
      jedis => {
        jedis.hkeys(key).forEach(field => jedis.hdel(key, field))
      }
    }.toOption
  }

  def hGetAllInt(key: String): Future[Map[String, Int]] = Future {
    import scala.jdk.CollectionConverters.*
    Using(pool.getResource) {
      jedis => jedis.hgetAll(key).asScala.view.mapValues(_.toInt).toMap
    }.toOption.getOrElse(Map.empty)
  }

  def get[T: Decoder](key: String): Future[Option[T]] = Future {
    Using(pool.getResource) {
      jedis => parser.parse(jedis.get(key)).flatMap(_.as[T]).toOption
    }.toOption.flatten
  }

  def cached[T: {Decoder, Encoder}](key: String, valueToCache: => Future[T], exp: Int = 60): Future[T] = {
    get(key).flatMap {
      case Some(v) => Future.successful(v)
      case None => valueToCache.flatMap(set(key, _))
    }
  }
}
