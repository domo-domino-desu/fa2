package me.domino.fa2.data.remote.fa.journal

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.CachedPageStoreSupport
import me.domino.fa2.data.remote.fa.core.requireStoreValue
import me.domino.fa2.data.remote.fa.core.storeJson
import me.domino.fa2.utils.parseJournalId

/** Journal 详情存储层。 */
class JournalPageCache(
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
  fun streamById(journalId: Int): Flow<ResourceResult<JournalDetail>> =
      cachedStore.stream(journalId)

  /** 单次读取详情。 */
  suspend fun loadById(journalId: Int): ResourceResult<JournalDetail> =
      cachedStore.loadOnce(journalId)

  /** 按 URL 读取详情。 */
  suspend fun loadByUrl(url: String): ResourceResult<JournalDetail> {
    val parsedId = parseJournalId(url)
    if (parsedId != null) {
      return loadById(parsedId)
    }
    val remote = dataSource.fetchByUrl(url)
    if (remote is ResourceResult.Success) {
      cachedStore.writeThrough(remote.data.id, remote.data)
    }
    return remote
  }

  private fun cacheKeyFor(journalId: Int): String = "journal:id=$journalId"

  companion object {
    private const val PAGE_TYPE_JOURNAL: String = "journal_detail_v1"
  }
}
