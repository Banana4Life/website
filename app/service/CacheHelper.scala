package service

import scala.concurrent.duration.{Duration, DurationInt}

object CacheHelper {
    val CacheDuration: Duration = 2.hours

    val BaseCacheKey = "banana4life"
    val ProjectsCacheKey = s"$BaseCacheKey.projects"
    val BlogCacheKey = s"$BaseCacheKey.blog.posts"
    val BlogCountCacheKey = s"$BaseCacheKey.blog.count"

    def jamNode(id: Int) = s"$BaseCacheKey.ldjam.node.$id"

    def jamUserFeed(id: Int) = s"$BaseCacheKey.ldjam.feed.$id"

    def jamEntry(repo: String) = s"$BaseCacheKey.ldjam.feed.$repo"

}
