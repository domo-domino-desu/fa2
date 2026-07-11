package me.domino.fa2.data.remote.fa.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.fake.InMemoryPageCacheDao

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
}
