package me.domino.fa2.data.store

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.FavoritesDataSource
import me.domino.fa2.data.datasource.GalleryDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.PageState
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

/** Gallery/Favorites/Scraps 存储层。 */
class GalleryStore(
    private val galleryDataSource: GalleryDataSource,
    private val favoritesDataSource: FavoritesDataSource,
    private val pageCacheDao: PageCacheDao,
) {
  private val store: Store<SectionPageKey, GalleryPage> = buildStore()

  /** 子分区类型。 */
  enum class Section(val routeKey: String, val pageType: String) {
    Gallery(routeKey = "gallery", pageType = "gallery_page_v1"),
    Favorites(routeKey = "favorites", pageType = "favorites_page_v1"),
  }

  /** 读取分页流。 */
  fun stream(
      section: Section,
      username: String,
      nextPageUrl: String?,
  ): Flow<PageState<GalleryPage>> {
    val key =
        SectionPageKey(
            section = section,
            username = normalizeUsername(username),
            nextPageUrl = nextPageUrl?.trim()?.takeIf { it.isNotBlank() },
        )
    return store
        .stream(StoreReadRequest.cached(key, true))
        .map(::toPageState)
        .flowWithInitialLoading()
  }

  /** 单次读取分页。 */
  suspend fun loadPageOnce(
      section: Section,
      username: String,
      nextPageUrl: String?,
  ): PageState<GalleryPage> =
      stream(section, username, nextPageUrl).first { state -> state !is PageState.Loading }

  /** 失效指定分区的全部缓存（含 Store 内存层）。 */
  suspend fun invalidateSection(section: Section) {
    val entries = pageCacheDao.listByPageType(section.pageType)
    entries.forEach { entity ->
      val key = parseSectionPageKey(section = section, cacheKey = entity.cacheKey)
      if (key != null) {
        store.clear(key)
      } else {
        pageCacheDao.delete(entity.cacheKey)
      }
    }
  }

  private fun buildStore(): Store<SectionPageKey, GalleryPage> {
    val fetcher =
        Fetcher.of<SectionPageKey, GalleryPage>(name = "gallery-sections-fetcher") { key ->
          when (key.section) {
            Section.Gallery -> galleryDataSource.fetchPage(key.username, key.nextPageUrl)
            Section.Favorites -> favoritesDataSource.fetchPage(key.username, key.nextPageUrl)
          }.requireStoreValue()
        }

    val sourceOfTruth =
        SourceOfTruth.of<SectionPageKey, GalleryPage, GalleryPage>(
            reader = { key ->
              pageCacheDao.observeByKey(cacheKeyFor(key)).map { entity ->
                readCacheIfValid(
                    entity = entity,
                    expectedPageType = key.section.pageType,
                    decode = ::decodePage,
                )
              }
            },
            writer = { key, page ->
              pageCacheDao.upsert(
                  PageCacheEntity(
                      cacheKey = cacheKeyFor(key),
                      pageType = key.section.pageType,
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

  private fun decodePage(entity: PageCacheEntity): GalleryPage? =
      runCatching { storeJson.decodeFromString<GalleryPage>(entity.dataJson) }.getOrNull()

  private fun cacheKeyFor(key: SectionPageKey): String {
    val cursor = key.nextPageUrl?.ifBlank { null } ?: "first"
    return "${key.section.routeKey}:username=${key.username}:cursor=$cursor"
  }

  private fun parseSectionPageKey(section: Section, cacheKey: String): SectionPageKey? {
    val prefix = "${section.routeKey}:username="
    if (!cacheKey.startsWith(prefix)) return null
    val marker = ":cursor="
    val markerIndex = cacheKey.indexOf(marker, startIndex = prefix.length)
    if (markerIndex < 0) return null
    val username = cacheKey.substring(prefix.length, markerIndex).trim()
    if (username.isBlank()) return null
    val cursorRaw = cacheKey.substring(markerIndex + marker.length)
    val nextPageUrl = cursorRaw.takeUnless { it == "first" }
    return SectionPageKey(
        section = section,
        username = normalizeUsername(username),
        nextPageUrl = nextPageUrl,
    )
  }

  private fun normalizeUsername(username: String): String = username.trim().lowercase()

  private data class SectionPageKey(
      val section: Section,
      val username: String,
      val nextPageUrl: String?,
  )
}

private fun Flow<PageState<GalleryPage>?>.flowWithInitialLoading(): Flow<PageState<GalleryPage>> =
    flow {
      emit(PageState.Loading)
      collect { state -> if (state != null) emit(state) }
    }
