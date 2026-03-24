package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import me.domino.fa2.data.datasource.UserDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

/**
 * User Header 存储层。
 */
class UserStore(
    private val dataSource: UserDataSource,
    private val pageCacheDao: PageCacheDao,
) {
    private val store: Store<String, User> = buildStore()

    /**
     * 读取 user header 流。
     */
    fun stream(username: String): Flow<PageState<User>> {
        val key = normalizeUsername(username)
        return store.stream(StoreReadRequest.cached(key, true))
            .map(::toPageState)
            .flowWithInitialLoading()
    }

    /**
     * 单次读取 user header。
     */
    suspend fun loadOnce(username: String): PageState<User> =
        stream(username).first { state -> state !is PageState.Loading }

    /**
     * 主动失效指定用户头部缓存。
     */
    suspend fun invalidate(username: String) {
        store.clear(normalizeUsername(username))
    }

    private fun buildStore(): Store<String, User> {
        val fetcher = Fetcher.of<String, User>(name = "user-fetcher") { username ->
            dataSource.fetchUser(username).requireStoreValue()
        }

        val sourceOfTruth = SourceOfTruth.of<String, User, User>(
            reader = { username ->
                pageCacheDao.observeByKey(cacheKeyFor(username)).map { entity ->
                    readCacheIfValid(
                        entity = entity,
                        expectedPageType = PAGE_TYPE_USER,
                        decode = ::decodeUser,
                    )
                }
            },
            writer = { username, header ->
                pageCacheDao.upsert(
                    PageCacheEntity(
                        cacheKey = cacheKeyFor(username),
                        pageType = PAGE_TYPE_USER,
                        dataJson = storeJson.encodeToString(header),
                        cachedAtMs = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            },
            delete = { username ->
                pageCacheDao.delete(cacheKeyFor(username))
            },
            deleteAll = {
                pageCacheDao.deleteAll()
            },
        )

        return StoreBuilder.from(fetcher = fetcher, sourceOfTruth = sourceOfTruth).build()
    }

    private fun decodeUser(entity: PageCacheEntity): User? =
        runCatching { storeJson.decodeFromString<User>(entity.dataJson) }.getOrNull()

    private fun cacheKeyFor(username: String): String = "user:username=${normalizeUsername(username)}"

    private fun normalizeUsername(username: String): String = username.trim().lowercase()

    companion object {
        private const val PAGE_TYPE_USER: String = "user_header_v1"
    }
}

private fun Flow<PageState<User>?>.flowWithInitialLoading(): Flow<PageState<User>> = flow {
    emit(PageState.Loading)
    collect { state ->
        if (state != null) emit(state)
    }
}
