package me.domino.fa2.data.remote.fa.core

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.ResourceResult
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

internal class CachedPageStoreSupport<Key : Any, Value : Any>(
    storeName: String,
    private val pageCacheDao: PageCacheDao,
    private val pageTypeOf: (Key) -> String,
    private val cacheKeyFor: (Key) -> String,
    fetch: suspend (Key) -> Value,
    private val encode: (Value) -> String,
    private val decode: (String) -> Value?,
) {
  private val store: Store<Key, Value> =
      StoreBuilder.from(
              fetcher = Fetcher.of<Key, Value>(name = storeName, fetch),
              sourceOfTruth =
                  SourceOfTruth.of<Key, Value, Value>(
                      reader = { key ->
                        pageCacheDao.observeByKey(cacheKeyFor(key)).map { entity ->
                          readCacheIfValid(
                              entity = entity,
                              expectedPageType = pageTypeOf(key),
                              decode = { cached -> decode(cached.dataJson) },
                          )
                        }
                      },
                      writer = { key, value ->
                        pageCacheDao.upsert(
                            PageCacheEntity(
                                cacheKey = cacheKeyFor(key),
                                pageType = pageTypeOf(key),
                                dataJson = encode(value),
                                cachedAtMs = Clock.System.now().toEpochMilliseconds(),
                            )
                        )
                      },
                      delete = { key -> pageCacheDao.delete(cacheKeyFor(key)) },
                      deleteAll = { pageCacheDao.deleteAll() },
                  ),
          )
          .build()

  fun stream(key: Key): Flow<ResourceResult<Value>> =
      store.stream(StoreReadRequest.cached(key, true)).mapNotNull(::toResourceResult)

  suspend fun loadOnce(key: Key): ResourceResult<Value> = stream(key).first()

  suspend fun refresh(key: Key): ResourceResult<Value> {
    store.clear(key)
    return loadOnce(key)
  }

  suspend fun prefetch(key: Key) {
    store.stream(StoreReadRequest.fresh(key, false)).first(::isTerminalResponse)
  }

  suspend fun clear(key: Key) {
    store.clear(key)
  }

  suspend fun writeThrough(key: Key, value: Value) {
    pageCacheDao.upsert(
        PageCacheEntity(
            cacheKey = cacheKeyFor(key),
            pageType = pageTypeOf(key),
            dataJson = encode(value),
            cachedAtMs = Clock.System.now().toEpochMilliseconds(),
        )
    )
  }
}
