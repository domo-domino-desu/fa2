package me.domino.fa2.data.remote.fa.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.fake.InMemoryPageCacheDao

@OptIn(ExperimentalCoroutinesApi::class)
class PageQueryCacheTest {
  @Test
  fun freshCacheSkipsFetch() = runTest {
    val dao = InMemoryPageCacheDao()
    dao.upsert(
        PageCacheEntity(
            "key",
            PageType.Feed.id,
            "cached",
            Clock.System.now().toEpochMilliseconds(),
        )
    )
    var fetches = 0
    val result =
        PageQueryCache(dao).queryOnce("key", PageType.Feed, { it }, { it }) {
          fetches += 1
          ResourceResult.Success("remote")
        }
    assertEquals("cached", assertIs<ResourceResult.Success<String>>(result).data)
    assertEquals(0, fetches)
  }

  @Test
  fun staleFailureKeepsCachedData() = runTest {
    val dao = InMemoryPageCacheDao()
    dao.upsert(PageCacheEntity("key", PageType.Feed.id, "cached", 0))
    val states =
        PageQueryCache(dao)
            .queryFlow("key", PageType.Feed, { it }, { it }) {
              ResourceResult.Error(IllegalStateException("boom"))
            }
            .take(2)
            .toList()
    assertEquals("cached", assertIs<QueryState.Success<String>>(states[0]).data)
    assertEquals("cached", assertIs<QueryState.Failure<String>>(states[1]).data)
  }

  @Test
  fun queryOnceRefetchesOnlyAfterInvalidate() = runTest {
    val dao = InMemoryPageCacheDao()
    val cache = PageQueryCache(dao)
    var fetches = 0
    val fetch: suspend () -> ResourceResult<String> = {
      fetches += 1
      ResourceResult.Success("v$fetches")
    }

    val first = cache.queryOnce("key", PageType.Feed, { it }, { it }, fetch)
    assertEquals("v1", assertIs<ResourceResult.Success<String>>(first).data)
    assertEquals(1, fetches)

    val stillFresh = cache.queryOnce("key", PageType.Feed, { it }, { it }, fetch)
    assertEquals("v1", assertIs<ResourceResult.Success<String>>(stillFresh).data)
    assertEquals(1, fetches)

    cache.invalidate("key")
    val afterInvalidate = cache.queryOnce("key", PageType.Feed, { it }, { it }, fetch)
    assertEquals("v2", assertIs<ResourceResult.Success<String>>(afterInvalidate).data)
    assertEquals(2, fetches)
  }

  @Test
  fun invalidateByTypeMarksOnlyMatchingEntriesStale() = runTest {
    val dao = InMemoryPageCacheDao()
    val now = Clock.System.now().toEpochMilliseconds()
    dao.upsert(PageCacheEntity("key1", PageType.Favorites.id, "a", now))
    dao.upsert(PageCacheEntity("key2", PageType.Favorites.id, "b", now))
    dao.upsert(PageCacheEntity("key3", PageType.Gallery.id, "c", now))

    PageQueryCache(dao).invalidate(PageType.Favorites)

    assertEquals(0, dao.findByKey("key1")!!.cachedAtMs)
    assertEquals(0, dao.findByKey("key2")!!.cachedAtMs)
    assertEquals(now, dao.findByKey("key3")!!.cachedAtMs)
  }

  @Test
  fun invalidateKeyTriggersRefetchForActiveQueryFlow() = runTest {
    val dao = InMemoryPageCacheDao()
    dao.upsert(
        PageCacheEntity(
            "key",
            PageType.Feed.id,
            "cached-v1",
            Clock.System.now().toEpochMilliseconds(),
        )
    )
    val cache = PageQueryCache(dao)
    var fetches = 0
    val states = mutableListOf<QueryState<String>>()
    val job = launch {
      cache
          .queryFlow("key", PageType.Feed, { it }, { it }) {
            fetches += 1
            ResourceResult.Success("cached-v2")
          }
          .collect { states += it }
    }
    runCurrent()
    assertEquals(0, fetches)
    assertEquals("cached-v1", assertIs<QueryState.Success<String>>(states.last()).data)

    cache.invalidate("key")
    runCurrent()
    job.cancel()

    assertEquals(1, fetches)
    assertEquals("cached-v2", assertIs<QueryState.Success<String>>(states.last()).data)
  }

  @Test
  fun concurrentFetchesForSameKeyDedupeToOneCall() = runTest {
    val dao = InMemoryPageCacheDao()
    val cache = PageQueryCache(dao)
    var fetches = 0
    val results =
        List(3) {
              async {
                cache.queryOnce("key", PageType.Feed, { it }, { it }) {
                  fetches += 1
                  delay(10)
                  ResourceResult.Success("remote-$fetches")
                }
              }
            }
            .awaitAll()

    assertEquals(1, fetches)
    results.forEach { result ->
      assertEquals("remote-1", assertIs<ResourceResult.Success<String>>(result).data)
    }
  }
}
