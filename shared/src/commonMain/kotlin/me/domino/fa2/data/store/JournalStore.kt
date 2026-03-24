package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import me.domino.fa2.data.datasource.JournalDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.PageState
import me.domino.fa2.util.ParserUtils
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

/**
 * Journal 详情存储层。
 */
class JournalStore(
    private val dataSource: JournalDataSource,
    private val pageCacheDao: PageCacheDao,
) {
    private val store: Store<Int, JournalDetail> = buildStore()

    /**
     * 读取详情流。
     */
    fun streamById(journalId: Int): Flow<PageState<JournalDetail>> =
        store.stream(StoreReadRequest.cached(journalId, true))
            .map(::toPageState)
            .flowWithInitialLoading()

    /**
     * 单次读取详情。
     */
    suspend fun loadById(journalId: Int): PageState<JournalDetail> =
        streamById(journalId).first { state -> state !is PageState.Loading }

    /**
     * 按 URL 读取详情。
     */
    suspend fun loadByUrl(url: String): PageState<JournalDetail> {
        val parsedId = ParserUtils.parseJournalId(url)
        if (parsedId != null) {
            return loadById(parsedId)
        }
        val remote = dataSource.fetchByUrl(url)
        if (remote is PageState.Success) {
            writeCacheById(remote.data.id, remote.data)
        }
        return remote
    }

    private fun buildStore(): Store<Int, JournalDetail> {
        val fetcher = Fetcher.of<Int, JournalDetail>(name = "journal-fetcher") { journalId ->
            dataSource.fetchById(journalId).requireStoreValue()
        }

        val sourceOfTruth = SourceOfTruth.of<Int, JournalDetail, JournalDetail>(
            reader = { journalId ->
                pageCacheDao.observeByKey(cacheKeyFor(journalId)).map { entity ->
                    readCacheIfValid(
                        entity = entity,
                        expectedPageType = PAGE_TYPE_JOURNAL,
                        decode = ::decodeDetail,
                    )
                }
            },
            writer = { journalId, detail ->
                writeCacheById(journalId, detail)
            },
            delete = { journalId ->
                pageCacheDao.delete(cacheKeyFor(journalId))
            },
            deleteAll = {
                pageCacheDao.deleteAll()
            },
        )

        return StoreBuilder.from(fetcher = fetcher, sourceOfTruth = sourceOfTruth).build()
    }

    private suspend fun writeCacheById(
        journalId: Int,
        detail: JournalDetail,
    ) {
        pageCacheDao.upsert(
            PageCacheEntity(
                cacheKey = cacheKeyFor(journalId),
                pageType = PAGE_TYPE_JOURNAL,
                dataJson = storeJson.encodeToString(detail),
                cachedAtMs = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    private fun decodeDetail(entity: PageCacheEntity): JournalDetail? =
        runCatching { storeJson.decodeFromString<JournalDetail>(entity.dataJson) }.getOrNull()

    private fun cacheKeyFor(journalId: Int): String = "journal:id=$journalId"

    companion object {
        private const val PAGE_TYPE_JOURNAL: String = "journal_detail_v1"
    }
}

private fun Flow<PageState<JournalDetail>?>.flowWithInitialLoading(): Flow<PageState<JournalDetail>> = flow {
    emit(PageState.Loading)
    collect { state ->
        if (state != null) emit(state)
    }
}
