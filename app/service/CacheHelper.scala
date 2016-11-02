package service

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

object CacheHelper {
    val CacheDuration: Duration = 2.hours

    val BaseCacheKey = "banana4life"
    val ProjectsCacheKey = s"$BaseCacheKey.projects"
    val BlogCacheKeyPrefix = s"$BaseCacheKey.blog.page"
    val BlogCountCacheKey = s"$BaseCacheKey.blog.count"
    val TwitterCacheKeyPrefix = s"$BaseCacheKey.twitter.page"
}
