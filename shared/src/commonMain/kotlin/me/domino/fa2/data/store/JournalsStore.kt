package me.domino.fa2.data.store

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.JournalsDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.PageState
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

/** Journals 分页存储层。 */
class JournalsStore(
  private val dataSource: JournalsDataSource,
  private val pageCacheDao: PageCacheDao,
) {
  private val store: Store<JournalsPageKey, JournalPage> = buildStore()

  /** 读取 journals 分页流。 */
  fun stream(username: String, nextPageUrl: String?): Flow<PageState<JournalPage>> {
    val key =
      JournalsPageKey(
        username = normalizeUsername(username),
        nextPageUrl = nextPageUrl?.trim()?.takeIf { it.isNotBlank() },
      )
    return store
      .stream(StoreReadRequest.cached(key, true))
      .map(::toPageState)
      .flowWithInitialLoading()
  }

  /** 单次读取 journals 分页。 */
  suspend fun loadPageOnce(username: String, nextPageUrl: String?): PageState<JournalPage> =
    stream(username, nextPageUrl).first { state -> state !is PageState.Loading }

  /** 失效指定用户 Journals 缓存（含 Store 内存层）。 */
  suspend fun invalidateUser(username: String) {
    val normalizedUsername = normalizeUsername(username)
    val prefix = "journals:username=$normalizedUsername:"
    val entries = pageCacheDao.listByPageType(PAGE_TYPE_JOURNALS)
    entries.forEach { entity ->
      if (!entity.cacheKey.startsWith(prefix)) return@forEach
      val parsed = parseJournalsPageKey(entity.cacheKey)
      if (parsed != null) {
        store.clear(parsed)
      } else {
        pageCacheDao.delete(entity.cacheKey)
      }
    }
  }

  private fun buildStore(): Store<JournalsPageKey, JournalPage> {
    val fetcher =
      Fetcher.of<JournalsPageKey, JournalPage>(name = "journals-fetcher") { key ->
        dataSource.fetchPage(key.username, key.nextPageUrl).requireStoreValue()
      }

    val sourceOfTruth =
      SourceOfTruth.of<JournalsPageKey, JournalPage, JournalPage>(
        reader = { key ->
          pageCacheDao.observeByKey(cacheKeyFor(key)).map { entity ->
            readCacheIfValid(
              entity = entity,
              expectedPageType = PAGE_TYPE_JOURNALS,
              decode = ::decodePage,
            )
          }
        },
        writer = { key, page ->
          pageCacheDao.upsert(
            PageCacheEntity(
              cacheKey = cacheKeyFor(key),
              pageType = PAGE_TYPE_JOURNALS,
              dataJson = storeJson.encodeToString(page),
              cachedAtMs = Clock.System.now().toEpochMilliseconds(),
            )
          )
        },
        delete = { key -> pageCacheDao.delete(cacheKeyFor(key)) },
        deleteAll = { pageCacheDao.deleteAll() },
      )

    return StoreBuilder.from(fetcher = fetcher, sourceOfTruth = sourceOfTruth).build()
  }

  private fun decodePage(entity: PageCacheEntity): JournalPage? =
    runCatching { storeJson.decodeFromString<JournalPage>(entity.dataJson) }.getOrNull()

  private fun cacheKeyFor(key: JournalsPageKey): String {
    val cursor = key.nextPageUrl?.ifBlank { null } ?: "first"
    return "journals:username=${key.username}:cursor=$cursor"
  }

  private fun normalizeUsername(username: String): String = username.trim().lowercase()

  private fun parseJournalsPageKey(cacheKey: String): JournalsPageKey? {
    val prefix = "journals:username="
    if (!cacheKey.startsWith(prefix)) return null
    val marker = ":cursor="
    val markerIndex = cacheKey.indexOf(marker, startIndex = prefix.length)
    if (markerIndex < 0) return null
    val username = cacheKey.substring(prefix.length, markerIndex).trim()
    if (username.isBlank()) return null
    val cursorRaw = cacheKey.substring(markerIndex + marker.length)
    val nextPageUrl = cursorRaw.takeUnless { it == "first" }
    return JournalsPageKey(username = normalizeUsername(username), nextPageUrl = nextPageUrl)
  }

  private data class JournalsPageKey(val username: String, val nextPageUrl: String?)

  companion object {
    private const val PAGE_TYPE_JOURNALS: String = "journals_page_v1"
  }
}

private fun Flow<PageState<JournalPage>?>.flowWithInitialLoading(): Flow<PageState<JournalPage>> =
  flow {
    emit(PageState.Loading)
    collect { state -> if (state != null) emit(state) }
  }
