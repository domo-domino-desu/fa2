package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.JournalsDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.PageState

/** Journals 分页存储层。 */
class JournalsStore(
    private val dataSource: JournalsDataSource,
    private val pageCacheDao: PageCacheDao,
) {
  private val cachedStore =
      CachedPageStoreSupport(
          storeName = "journals-fetcher",
          pageCacheDao = pageCacheDao,
          pageTypeOf = { PAGE_TYPE_JOURNALS },
          cacheKeyFor = ::cacheKeyFor,
          fetch = { key ->
            dataSource.fetchPage(key.username, key.nextPageUrl).requireStoreValue()
          },
          encode = { page -> storeJson.encodeToString(page) },
          decode = { json ->
            runCatching { storeJson.decodeFromString<JournalPage>(json) }.getOrNull()
          },
      )

  /** 读取 journals 分页流。 */
  fun stream(username: String, nextPageUrl: String?): Flow<PageState<JournalPage>> {
    val key =
        JournalsPageKey(
            username = normalizeUsername(username),
            nextPageUrl = nextPageUrl?.trim()?.takeIf { it.isNotBlank() },
        )
    return cachedStore.stream(key)
  }

  /** 单次读取 journals 分页。 */
  suspend fun loadPageOnce(username: String, nextPageUrl: String?): PageState<JournalPage> =
      cachedStore.loadOnce(
          JournalsPageKey(
              username = normalizeUsername(username),
              nextPageUrl = nextPageUrl?.trim()?.takeIf { it.isNotBlank() },
          )
      )

  /** 失效指定用户 Journals 缓存（含 Store 内存层）。 */
  suspend fun invalidateUser(username: String) {
    val normalizedUsername = normalizeUsername(username)
    val prefix = "journals:username=$normalizedUsername:"
    val entries = pageCacheDao.listByPageType(PAGE_TYPE_JOURNALS)
    entries.forEach { entity ->
      if (!entity.cacheKey.startsWith(prefix)) return@forEach
      val parsed = parseJournalsPageKey(entity.cacheKey)
      if (parsed != null) {
        cachedStore.clear(parsed)
      } else {
        pageCacheDao.delete(entity.cacheKey)
      }
    }
  }

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
