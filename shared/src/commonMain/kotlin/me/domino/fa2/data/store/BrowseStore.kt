package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import me.domino.fa2.data.datasource.BrowseDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionListingPage
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

/**
 * Browse 列表存储层。
 */
class BrowseStore(
    private val dataSource: BrowseDataSource,
    private val pageCacheDao: PageCacheDao,
) {
    private val store: Store<String, SubmissionListingPage> = buildStore()

    fun stream(requestUrl: String): Flow<PageState<SubmissionListingPage>> {
        val key = normalizeRequestUrl(requestUrl)
        return store.stream(StoreReadRequest.cached(key, true))
            .map(::toPageState)
            .flowWithInitialLoading()
    }

    suspend fun loadPageOnce(requestUrl: String): PageState<SubmissionListingPage> =
        stream(requestUrl = requestUrl).first { state -> state !is PageState.Loading }

    suspend fun refreshPage(requestUrl: String): PageState<SubmissionListingPage> {
        val key = normalizeRequestUrl(requestUrl)
        store.clear(key)
        return loadPageOnce(requestUrl)
    }

    private fun buildStore(): Store<String, SubmissionListingPage> {
        val fetcher = Fetcher.of<String, SubmissionListingPage>(name = "browse-fetcher") { url ->
            dataSource.fetchPage(url).requireStoreValue()
        }
        val sourceOfTruth = SourceOfTruth.of<String, SubmissionListingPage, SubmissionListingPage>(
            reader = { key ->
                pageCacheDao.observeByKey(cacheKeyFor(key)).map { entity ->
                    readCacheIfValid(
                        entity = entity,
                        expectedPageType = PAGE_TYPE_BROWSE,
                        decode = ::decodePage,
                    )
                }
            },
            writer = { key, page ->
                pageCacheDao.upsert(
                    PageCacheEntity(
                        cacheKey = cacheKeyFor(key),
                        pageType = PAGE_TYPE_BROWSE,
                        dataJson = storeJson.encodeToString(page),
                        cachedAtMs = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            },
            delete = { key ->
                pageCacheDao.delete(cacheKeyFor(key))
            },
            deleteAll = {
                pageCacheDao.deleteAll()
            },
        )
        return StoreBuilder.from(fetcher = fetcher, sourceOfTruth = sourceOfTruth).build()
    }

    private fun decodePage(entity: PageCacheEntity): SubmissionListingPage? =
        runCatching { storeJson.decodeFromString<SubmissionListingPage>(entity.dataJson) }.getOrNull()

    private fun normalizeRequestUrl(url: String): String = url.trim()

    private fun cacheKeyFor(url: String): String = "browse:url=$url"

    companion object {
        private const val PAGE_TYPE_BROWSE: String = "browse_page_v1"
    }
}

private fun Flow<PageState<SubmissionListingPage>?>.flowWithInitialLoading(): Flow<PageState<SubmissionListingPage>> =
    flow {
        emit(PageState.Loading)
        collect { state ->
            if (state != null) emit(state)
        }
    }
