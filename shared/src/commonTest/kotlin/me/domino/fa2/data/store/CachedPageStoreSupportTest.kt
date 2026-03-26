package me.domino.fa2.data.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.model.PageState
import me.domino.fa2.fake.InMemoryPageCacheDao

class CachedPageStoreSupportTest {
  @Test
  fun loadOnceUsesCacheUntilRefreshOrClear() = runTest {
    var fetchCount = 0
    val support =
        CachedPageStoreSupport(
            storeName = "test-cache-store",
            pageCacheDao = InMemoryPageCacheDao(),
            pageTypeOf = { "test_page_v1" },
            cacheKeyFor = { key: String -> "cache:$key" },
            fetch = { key ->
              fetchCount += 1
              CachedValue(label = "$key-$fetchCount")
            },
            encode = { value -> storeJson.encodeToString(value) },
            decode = { json -> storeJson.decodeFromString<CachedValue>(json) },
        )

    val first = support.loadOnce("alpha")
    val second = support.loadOnce("alpha")
    val refreshed = support.refresh("alpha")
    support.clear("alpha")
    val afterClear = support.loadOnce("alpha")

    assertEquals(PageState.Success(CachedValue("alpha-1")), first)
    assertEquals(PageState.Success(CachedValue("alpha-1")), second)
    assertEquals(PageState.Success(CachedValue("alpha-2")), refreshed)
    assertEquals(PageState.Success(CachedValue("alpha-3")), afterClear)
    assertEquals(3, fetchCount)
  }
}

@Serializable private data class CachedValue(val label: String)
