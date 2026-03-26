package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.UserDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User

/** User Header 存储层。 */
class UserStore(private val dataSource: UserDataSource, private val pageCacheDao: PageCacheDao) {
  private val cachedStore =
      CachedPageStoreSupport(
          storeName = "user-fetcher",
          pageCacheDao = pageCacheDao,
          pageTypeOf = { PAGE_TYPE_USER },
          cacheKeyFor = ::cacheKeyFor,
          fetch = { username -> dataSource.fetchUser(username).requireStoreValue() },
          encode = { header -> storeJson.encodeToString(header) },
          decode = { json -> runCatching { storeJson.decodeFromString<User>(json) }.getOrNull() },
      )

  /** 读取 user header 流。 */
  fun stream(username: String): Flow<PageState<User>> {
    val key = normalizeUsername(username)
    return cachedStore.stream(key)
  }

  /** 单次读取 user header。 */
  suspend fun loadOnce(username: String): PageState<User> =
      cachedStore.loadOnce(normalizeUsername(username))

  /** 主动失效指定用户头部缓存。 */
  suspend fun invalidate(username: String) {
    cachedStore.clear(normalizeUsername(username))
  }

  private fun cacheKeyFor(username: String): String = "user:username=${normalizeUsername(username)}"

  private fun normalizeUsername(username: String): String = username.trim().lowercase()

  companion object {
    private const val PAGE_TYPE_USER: String = "user_header_v1"
  }
}
