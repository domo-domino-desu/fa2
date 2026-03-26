package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.JournalDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.PageState
import me.domino.fa2.util.parseJournalId

/** Journal 详情存储层。 */
class JournalStore(
    private val dataSource: JournalDataSource,
    private val pageCacheDao: PageCacheDao,
) {
  private val cachedStore =
      CachedPageStoreSupport(
          storeName = "journal-fetcher",
          pageCacheDao = pageCacheDao,
          pageTypeOf = { PAGE_TYPE_JOURNAL },
          cacheKeyFor = ::cacheKeyFor,
          fetch = { journalId -> dataSource.fetchById(journalId).requireStoreValue() },
          encode = { detail -> storeJson.encodeToString(detail) },
          decode = { json ->
            runCatching { storeJson.decodeFromString<JournalDetail>(json) }.getOrNull()
          },
      )

  /** 读取详情流。 */
  fun streamById(journalId: Int): Flow<PageState<JournalDetail>> = cachedStore.stream(journalId)

  /** 单次读取详情。 */
  suspend fun loadById(journalId: Int): PageState<JournalDetail> = cachedStore.loadOnce(journalId)

  /** 按 URL 读取详情。 */
  suspend fun loadByUrl(url: String): PageState<JournalDetail> {
    val parsedId = parseJournalId(url)
    if (parsedId != null) {
      return loadById(parsedId)
    }
    val remote = dataSource.fetchByUrl(url)
    if (remote is PageState.Success) {
      cachedStore.writeThrough(remote.data.id, remote.data)
    }
    return remote
  }

  private fun cacheKeyFor(journalId: Int): String = "journal:id=$journalId"

  companion object {
    private const val PAGE_TYPE_JOURNAL: String = "journal_detail_v1"
  }
}
