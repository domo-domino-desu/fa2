package me.domino.fa2.data.fa.core

import kotlinx.coroutines.flow.Flow
import me.domino.fa2.data.model.PageState

/**
 * Shared page-resource facade for FA resources that follow fetch -> cache -> load/refresh/prefetch.
 */
internal class CachedPageResource<Key : Any, Value : Any>(
    storeName: String,
    pageCacheDao: me.domino.fa2.data.local.dao.PageCacheDao,
    pageTypeOf: (Key) -> String,
    cacheKeyFor: (Key) -> String,
    fetch: suspend (Key) -> Value,
    encode: (Value) -> String,
    decode: (String) -> Value?,
) {
  private val store =
      CachedPageStoreSupport(
          storeName = storeName,
          pageCacheDao = pageCacheDao,
          pageTypeOf = pageTypeOf,
          cacheKeyFor = cacheKeyFor,
          fetch = fetch,
          encode = encode,
          decode = decode,
      )

  fun stream(key: Key): Flow<PageState<Value>> = store.stream(key)

  suspend fun load(key: Key): PageState<Value> = store.loadOnce(key)

  suspend fun refresh(key: Key): PageState<Value> = store.refresh(key)

  suspend fun prefetch(key: Key) {
    store.prefetch(key)
  }

  suspend fun clear(key: Key) {
    store.clear(key)
  }
}
