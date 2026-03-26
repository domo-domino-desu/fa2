package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.SubmissionDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.util.parseSubmissionSid

/** Submission 详情存储层。 */
class SubmissionStore(
    private val dataSource: SubmissionDataSource,
    private val pageCacheDao: PageCacheDao,
) {
  private val cachedStore =
      CachedPageStoreSupport(
          storeName = "submission-fetcher",
          pageCacheDao = pageCacheDao,
          pageTypeOf = { PAGE_TYPE_SUBMISSION },
          cacheKeyFor = ::cacheKeyFor,
          fetch = { sid -> dataSource.fetchBySid(sid).requireStoreValue() },
          encode = { detail -> storeJson.encodeToString(detail) },
          decode = { json ->
            runCatching { storeJson.decodeFromString<Submission>(json) }.getOrNull()
          },
      )

  /** 按 ID 读取详情流。 */
  fun streamBySid(sid: Int): Flow<PageState<Submission>> = cachedStore.stream(sid)

  /** 按 ID 单次读取详情。 */
  suspend fun loadBySid(sid: Int): PageState<Submission> = cachedStore.loadOnce(sid)

  /** 按 URL 读取详情。 */
  suspend fun loadByUrl(url: String): PageState<Submission> {
    val sid = parseSubmissionSid(url)
    if (sid != null) {
      return loadBySid(sid)
    }
    val remote = dataSource.fetchByUrl(url)
    if (remote is PageState.Success) {
      cachedStore.writeThrough(remote.data.id, remote.data)
    }
    return remote
  }

  /** 预取指定投稿详情。 */
  suspend fun prefetchBySid(sid: Int) {
    cachedStore.prefetch(sid)
  }

  /** 主动失效指定投稿详情缓存。 */
  suspend fun invalidateBySid(sid: Int) {
    cachedStore.clear(sid)
  }

  private fun cacheKeyFor(sid: Int): String = "submission:sid=$sid"

  companion object {
    private const val PAGE_TYPE_SUBMISSION: String = "submission_detail_v1"
  }
}
