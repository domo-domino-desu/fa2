package me.domino.fa2.data.store

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.PageState
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

  fun stream(key: Key): Flow<PageState<Value>> =
      store.stream(StoreReadRequest.cached(key, true)).map(::toPageState).flowWithInitialLoading()

  suspend fun loadOnce(key: Key): PageState<Value> =
      stream(key).first { state -> state !is PageState.Loading }

  suspend fun refresh(key: Key): PageState<Value> {
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

internal fun <T : Any> Flow<PageState<T>?>.flowWithInitialLoading(): Flow<PageState<T>> = flow {
  emit(PageState.Loading)
  collect { state -> if (state != null) emit(state) }
}
