package me.domino.fa2.data.remote.fa.core

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.errorOrNull

private const val SHORT_PAGE_TTL_MS = 3 * 60 * 1000L
private const val LONG_PAGE_TTL_MS = 30 * 60 * 1000L

enum class PageType(val id: String, val ttlMs: Long) {
  Feed("feed_page_v1", SHORT_PAGE_TTL_MS),
  Browse("browse_page_v1", SHORT_PAGE_TTL_MS),
  Search("search_page_v1", SHORT_PAGE_TTL_MS),
  Gallery("gallery_page_v1", SHORT_PAGE_TTL_MS),
  Favorites("favorites_page_v1", SHORT_PAGE_TTL_MS),
  Journals("journals_page_v1", SHORT_PAGE_TTL_MS),
  Watchlist("watchlist_page_v1", SHORT_PAGE_TTL_MS),
  User("user_header_v2", LONG_PAGE_TTL_MS),
  Submission("submission_detail_v1", LONG_PAGE_TTL_MS),
  Journal("journal_detail_v1", LONG_PAGE_TTL_MS);

  companion object {
    fun fromId(id: String): PageType? = entries.firstOrNull { it.id == id }
  }
}

class PageQueryCache(private val dao: PageCacheDao) {
  private val locks = mutableMapOf<String, Mutex>()
  private val locksGuard = Mutex()

  fun <T : Any> queryFlow(
      key: String,
      type: PageType,
      encode: (T) -> String,
      decode: (String) -> T?,
      fetch: suspend () -> ResourceResult<T>,
  ): Flow<QueryState<T>> = channelFlow {
    var fetching = false
    dao.observeByKey(key).collect { entity ->
      val cached = entity?.takeIf { it.pageType == type.id }?.let { decode(it.dataJson) }
      val fresh = entity?.isFresh(type) == true
      if (cached == null) send(QueryState.Loading())
      else send(QueryState.Success(cached, refreshing = !fresh))
      if (!fresh && !fetching) {
        fetching = true
        when (val result = fetchFresh(key, type, encode, decode, fetch)) {
          is ResourceResult.Success -> Unit
          else -> send(QueryState.Failure(result.errorOrNull()!!, cached))
        }
        fetching = false
      }
    }
  }

  suspend fun <T : Any> queryOnce(
      key: String,
      type: PageType,
      encode: (T) -> String,
      decode: (String) -> T?,
      fetch: suspend () -> ResourceResult<T>,
  ): ResourceResult<T> {
    val entity = dao.findByKey(key)
    val cached =
        entity?.takeIf { it.pageType == type.id && it.isFresh(type) }?.let { decode(it.dataJson) }
    if (cached != null) return ResourceResult.Success(cached)
    return fetchFresh(key, type, encode, decode, fetch)
  }

  suspend fun invalidate(key: String) = dao.markStale(key)

  suspend fun invalidate(type: PageType) = dao.markTypeStale(type.id)

  private suspend fun <T : Any> fetchFresh(
      key: String,
      type: PageType,
      encode: (T) -> String,
      decode: (String) -> T?,
      fetch: suspend () -> ResourceResult<T>,
  ): ResourceResult<T> =
      lockFor(key).withLock {
        val current = dao.findByKey(key)
        val cached =
            current
                ?.takeIf { it.pageType == type.id && it.isFresh(type) }
                ?.let { decode(it.dataJson) }
        if (cached != null) return@withLock ResourceResult.Success(cached)
        fetch().also { result ->
          if (result is ResourceResult.Success) {
            dao.upsert(PageCacheEntity(key, type.id, encode(result.data), now()))
          }
        }
      }

  private suspend fun lockFor(key: String): Mutex =
      locksGuard.withLock { locks.getOrPut(key) { Mutex() } }
}

private fun PageCacheEntity.isFresh(type: PageType): Boolean = now() - cachedAtMs in 0..type.ttlMs

private fun now(): Long = Clock.System.now().toEpochMilliseconds()
