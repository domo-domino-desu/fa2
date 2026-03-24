package me.domino.fa2.data.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import me.domino.fa2.data.local.entity.PageCacheEntity

/** 页面缓存 TTL 策略测试。 */
class PageCacheTtlPolicyTest {
  @Test
  fun shortTtlUsesThreeMinutes() {
    val now = Clock.System.now().toEpochMilliseconds()
    val fresh =
      PageCacheEntity(
        cacheKey = "feed:fromSid=0",
        pageType = "feed_page_v1",
        dataJson = "{}",
        cachedAtMs = now - 2 * 60 * 1000L,
      )
    val expired = fresh.copy(cachedAtMs = now - 4 * 60 * 1000L)

    val freshValue =
      readCacheIfValid(entity = fresh, expectedPageType = "feed_page_v1", decode = { "fresh" })
    val expiredValue =
      readCacheIfValid(entity = expired, expectedPageType = "feed_page_v1", decode = { "expired" })

    assertEquals("fresh", freshValue)
    assertNull(expiredValue)
  }

  @Test
  fun longTtlUsesThirtyMinutes() {
    val now = Clock.System.now().toEpochMilliseconds()
    val fresh =
      PageCacheEntity(
        cacheKey = "user:username=terriniss",
        pageType = "user_header_v1",
        dataJson = "{}",
        cachedAtMs = now - 20 * 60 * 1000L,
      )
    val expired = fresh.copy(cachedAtMs = now - 40 * 60 * 1000L)

    val freshValue =
      readCacheIfValid(entity = fresh, expectedPageType = "user_header_v1", decode = { "fresh" })
    val expiredValue =
      readCacheIfValid(
        entity = expired,
        expectedPageType = "user_header_v1",
        decode = { "expired" },
      )

    assertEquals("fresh", freshValue)
    assertNull(expiredValue)
  }

  @Test
  fun watchlistUsesShortTtl() {
    val now = Clock.System.now().toEpochMilliseconds()
    val expired =
      PageCacheEntity(
        cacheKey = "watchlist:category=watching:username=terriniss:cursor=first",
        pageType = "watchlist_page_v1",
        dataJson = "{}",
        cachedAtMs = now - 5 * 60 * 1000L,
      )

    val value =
      readCacheIfValid(
        entity = expired,
        expectedPageType = "watchlist_page_v1",
        decode = { "watchlist" },
      )

    assertNull(value)
  }
}
