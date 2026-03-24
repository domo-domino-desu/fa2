package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import me.domino.fa2.data.datasource.SubmissionDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.util.ParserUtils
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

/**
 * Submission 详情存储层。
 */
class SubmissionStore(
    private val dataSource: SubmissionDataSource,
    private val pageCacheDao: PageCacheDao,
) {
    private val store: Store<Int, Submission> = buildStore()

    /**
     * 按 ID 读取详情流。
     */
    fun streamBySid(sid: Int): Flow<PageState<Submission>> =
        store.stream(StoreReadRequest.cached(sid, true))
            .map(::toPageState)
            .flowWithInitialLoading()

    /**
     * 按 ID 单次读取详情。
     */
    suspend fun loadBySid(sid: Int): PageState<Submission> =
        streamBySid(sid).first { state -> state !is PageState.Loading }

    /**
     * 按 URL 读取详情。
     */
    suspend fun loadByUrl(url: String): PageState<Submission> {
        val sid = ParserUtils.parseSubmissionSid(url)
        if (sid != null) {
            return loadBySid(sid)
        }
        val remote = dataSource.fetchByUrl(url)
        if (remote is PageState.Success) {
            writeCacheBySid(remote.data.id, remote.data)
        }
        return remote
    }

    /**
     * 预取指定投稿详情。
     */
    suspend fun prefetchBySid(sid: Int) {
        store.stream(StoreReadRequest.fresh(sid, false))
            .first(::isTerminalResponse)
    }

    /**
     * 主动失效指定投稿详情缓存。
     */
    suspend fun invalidateBySid(sid: Int) {
        store.clear(sid)
    }

    private fun buildStore(): Store<Int, Submission> {
        val fetcher = Fetcher.of<Int, Submission>(name = "submission-fetcher") { sid ->
            dataSource.fetchBySid(sid).requireStoreValue()
        }

        val sourceOfTruth = SourceOfTruth.of<Int, Submission, Submission>(
            reader = { sid ->
                pageCacheDao.observeByKey(cacheKeyFor(sid)).map { entity ->
                    readCacheIfValid(
                        entity = entity,
                        expectedPageType = PAGE_TYPE_SUBMISSION,
                        decode = ::decodeDetail,
                    )
                }
            },
            writer = { sid, detail ->
                writeCacheBySid(sid, detail)
            },
            delete = { sid ->
                pageCacheDao.delete(cacheKeyFor(sid))
            },
            deleteAll = {
                pageCacheDao.deleteAll()
            },
        )

        return StoreBuilder.from(fetcher = fetcher, sourceOfTruth = sourceOfTruth).build()
    }

    private suspend fun writeCacheBySid(
        sid: Int,
        detail: Submission,
    ) {
        pageCacheDao.upsert(
            PageCacheEntity(
                cacheKey = cacheKeyFor(sid),
                pageType = PAGE_TYPE_SUBMISSION,
                dataJson = storeJson.encodeToString(detail),
                cachedAtMs = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    private fun decodeDetail(entity: PageCacheEntity): Submission? =
        runCatching { storeJson.decodeFromString<Submission>(entity.dataJson) }.getOrNull()

    private fun cacheKeyFor(sid: Int): String = "submission:sid=$sid"

    companion object {
        private const val PAGE_TYPE_SUBMISSION: String = "submission_detail_v1"
    }
}

private fun Flow<PageState<Submission>?>.flowWithInitialLoading(): Flow<PageState<Submission>> = flow {
    emit(PageState.Loading)
    collect { state ->
        if (state != null) emit(state)
    }
}
