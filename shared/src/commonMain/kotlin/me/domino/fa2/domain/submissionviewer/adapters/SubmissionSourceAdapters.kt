package me.domino.fa2.domain.submissionviewer.adapters

import io.ktor.http.encodeURLParameter
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.remote.fa.browse.BrowseRepository
import me.domino.fa2.data.remote.fa.favorites.FavoritesRepository
import me.domino.fa2.data.remote.fa.feed.FeedRepository
import me.domino.fa2.data.remote.fa.gallery.GalleryRepository
import me.domino.fa2.data.remote.fa.search.SearchRepository
import me.domino.fa2.data.remote.fa.submission.SubmissionSeriesSubmissionSource
import me.domino.fa2.domain.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.domain.submissionseries.SubmissionSeriesRule
import me.domino.fa2.domain.submissionseries.SubmissionSeriesService
import me.domino.fa2.domain.submissionseries.normalizeSubmissionSeriesUrl
import me.domino.fa2.domain.submissionseries.toSubmissionThumbnail
import me.domino.fa2.domain.submissionviewer.api.SubmissionContextSourceKind
import me.domino.fa2.domain.submissionviewer.api.SubmissionPageSnapshot
import me.domino.fa2.domain.submissionviewer.api.SubmissionPaginationKind
import me.domino.fa2.domain.submissionviewer.api.SubmissionPaginationModel
import me.domino.fa2.domain.submissionviewer.api.SubmissionSourceAdapter
import me.domino.fa2.domain.submissionviewer.api.UserSubmissionSection

internal class FeedSubmissionSourceAdapter(private val repository: FeedRepository) :
    SubmissionSourceAdapter() {
  override val sourceKind: SubmissionContextSourceKind = SubmissionContextSourceKind.FEED
  override val paginationModel: SubmissionPaginationModel =
      SubmissionPaginationModel(
          kind = SubmissionPaginationKind.CURSOR,
          hasFirstPage = true,
          knowsLastPage = false,
      )

  override suspend fun loadInitialPage(): ResourceResult<SubmissionPageSnapshot> =
      repository.loadFirstPage().mapLoadedPage { page ->
        page.toLoadedPage(
            requestKey = page.currentPageUrl,
            pageId = page.currentPageUrl ?: "feed:first",
        )
      }

  override suspend fun loadNextPage(requestKey: String): ResourceResult<SubmissionPageSnapshot> =
      repository.loadPageByUrl(requestKey).mapLoadedPage { page ->
        page.toLoadedPage(requestKey = requestKey, pageId = requestKey)
      }

  override suspend fun loadPreviousPage(
      requestKey: String
  ): ResourceResult<SubmissionPageSnapshot> =
      repository.loadPageByUrl(requestKey).mapLoadedPage { page ->
        page.toLoadedPage(requestKey = requestKey, pageId = requestKey)
      }

  override suspend fun loadFirstPage(): ResourceResult<SubmissionPageSnapshot> =
      repository.loadPageByUrl(me.domino.fa2.utils.FaUrls.submissions()).mapLoadedPage { page ->
        page.toLoadedPage(
            requestKey = page.currentPageUrl ?: me.domino.fa2.utils.FaUrls.submissions(),
            pageId = page.currentPageUrl ?: "feed:first",
        )
      }
}

internal class SearchSubmissionSourceAdapter(
    private val repository: SearchRepository,
    private val firstPageUrl: String,
) : SubmissionSourceAdapter() {
  override val sourceKind: SubmissionContextSourceKind = SubmissionContextSourceKind.SEARCH
  override val paginationModel: SubmissionPaginationModel =
      SubmissionPaginationModel(
          kind = SubmissionPaginationKind.NUMBERED,
          hasFirstPage = true,
          knowsLastPage = true,
      )

  override suspend fun loadInitialPage(): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(firstPageUrl)

  override suspend fun loadNextPage(requestKey: String): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(requestKey)

  override suspend fun loadPreviousPage(
      requestKey: String
  ): ResourceResult<SubmissionPageSnapshot> = loadByUrl(requestKey)

  override suspend fun loadFirstPage(): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(firstPageUrl)

  override suspend fun loadLastPage(): ResourceResult<SubmissionPageSnapshot> {
    val first = loadInitialPage()
    val page = (first as? ResourceResult.Success)?.data ?: return first
    val totalCount =
        page.totalCount ?: return ResourceResult.Error(IllegalStateException("No total"))
    val lastPageNumber = ((minOf(totalCount, searchResultCountUpperBound) - 1) / searchPageSize) + 1
    return jumpToPage(lastPageNumber)
  }

  override suspend fun jumpToPage(pageNumber: Int): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(replaceQueryParam(firstPageUrl, "page", pageNumber.toString()))

  private suspend fun loadByUrl(url: String): ResourceResult<SubmissionPageSnapshot> =
      repository.loadPage(url).mapLoadedPage { page ->
        page.toLoadedPage(
            requestKey = url,
            pageId = "search:$url",
            firstPageUrl = replaceQueryParam(url, "page", "1"),
            lastPageNumber =
                page.totalCount?.let { total ->
                  ((minOf(total, searchResultCountUpperBound) - 1) / searchPageSize) + 1
                },
            lastPageUrl =
                page.totalCount?.let { total ->
                  replaceQueryParam(
                      url,
                      "page",
                      (((minOf(total, searchResultCountUpperBound) - 1) / searchPageSize) + 1)
                          .toString(),
                  )
                },
        )
      }
}

internal class BrowseSubmissionSourceAdapter(
    private val repository: BrowseRepository,
    private val firstPageUrl: String,
) : SubmissionSourceAdapter() {
  override val sourceKind: SubmissionContextSourceKind = SubmissionContextSourceKind.BROWSE
  override val paginationModel: SubmissionPaginationModel =
      SubmissionPaginationModel(
          kind = SubmissionPaginationKind.NUMBERED,
          hasFirstPage = true,
          knowsLastPage = false,
      )

  override suspend fun loadInitialPage(): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(firstPageUrl)

  override suspend fun loadNextPage(requestKey: String): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(requestKey)

  override suspend fun loadPreviousPage(
      requestKey: String
  ): ResourceResult<SubmissionPageSnapshot> = loadByUrl(requestKey)

  override suspend fun loadFirstPage(): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(firstPageUrl)

  override suspend fun jumpToPage(pageNumber: Int): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(replaceQueryParam(firstPageUrl, "page", pageNumber.toString()))

  private suspend fun loadByUrl(url: String): ResourceResult<SubmissionPageSnapshot> =
      repository.loadPage(url).mapLoadedPage { page ->
        page.toLoadedPage(
            requestKey = url,
            pageId = "browse:$url",
            firstPageUrl = replaceQueryParam(url, "page", "1"),
            lastPageNumber = null,
            lastPageUrl = null,
        )
      }
}

internal class UserSubmissionSourceAdapter(
    private val route: UserSubmissionSection,
    private val username: String,
    private val initialPageUrl: String?,
    private val galleryRepository: GalleryRepository,
    private val favoritesRepository: FavoritesRepository,
) : SubmissionSourceAdapter() {
  private val lastPageCacheKey: String? =
      if (route == UserSubmissionSection.Gallery) {
        listOf(route.routeKey, username.lowercase(), initialPageUrl?.trim().orEmpty())
            .joinToString(separator = "|")
      } else {
        null
      }
  private var resolvedGalleryLastPageNumber: Int? =
      lastPageCacheKey?.let(UserSubmissionLastPageHintCache::get)

  override val sourceKind: SubmissionContextSourceKind =
      when (route) {
        UserSubmissionSection.Gallery -> SubmissionContextSourceKind.GALLERY
        UserSubmissionSection.Favorites -> SubmissionContextSourceKind.FAVORITES
        UserSubmissionSection.Journals -> SubmissionContextSourceKind.GALLERY
      }

  override val paginationModel: SubmissionPaginationModel =
      when (route) {
        UserSubmissionSection.Gallery ->
            SubmissionPaginationModel(
                kind = SubmissionPaginationKind.NUMBERED,
                hasFirstPage = true,
                knowsLastPage = true,
            )

        UserSubmissionSection.Favorites ->
            SubmissionPaginationModel(
                kind = SubmissionPaginationKind.CURSOR,
                hasFirstPage = true,
                knowsLastPage = false,
            )

        UserSubmissionSection.Journals -> SubmissionPaginationModel()
      }

  override suspend fun loadInitialPage(): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(initialPageUrl)

  override suspend fun loadNextPage(requestKey: String): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(requestKey)

  override suspend fun loadPreviousPage(
      requestKey: String
  ): ResourceResult<SubmissionPageSnapshot> =
      if (route == UserSubmissionSection.Journals) {
        ResourceResult.Error(
            UnsupportedOperationException("Previous page is unsupported for $sourceKind")
        )
      } else {
        loadByUrl(requestKey)
      }

  override suspend fun loadFirstPage(): ResourceResult<SubmissionPageSnapshot> =
      loadByUrl(
          when (route) {
            UserSubmissionSection.Gallery -> null
            UserSubmissionSection.Favorites -> null
            UserSubmissionSection.Journals -> initialPageUrl
          }
      )

  override suspend fun jumpToPage(pageNumber: Int): ResourceResult<SubmissionPageSnapshot> {
    if (route != UserSubmissionSection.Gallery) {
      return ResourceResult.Error(
          UnsupportedOperationException("Jump page is unsupported for $sourceKind")
      )
    }
    val targetPageNumber = pageNumber.coerceAtLeast(1)
    val knownLastPageNumber = resolvedGalleryLastPageNumber
    if (knownLastPageNumber != null && targetPageNumber > knownLastPageNumber) {
      return loadGalleryPageNumber(
          knownLastPageNumber,
          lastPageNumberOverride = knownLastPageNumber,
      )
    }
    val result = loadGalleryPageNumber(targetPageNumber)
    if (targetPageNumber <= 1 || route != UserSubmissionSection.Gallery) return result
    val loadedPage = (result as? ResourceResult.Success)?.data ?: return result
    return if (loadedPage.items.isEmpty()) {
      resolveGalleryLastPage(initialUpperBound = targetPageNumber)
    } else {
      result
    }
  }

  override suspend fun loadLastPage(): ResourceResult<SubmissionPageSnapshot> {
    if (route != UserSubmissionSection.Gallery) {
      return ResourceResult.Error(
          UnsupportedOperationException("Last page is unsupported for $sourceKind")
      )
    }
    return resolveGalleryLastPage()
  }

  private suspend fun loadByUrl(
      url: String?,
      lastPageNumberOverride: Int? = resolvedGalleryLastPageNumber,
  ): ResourceResult<SubmissionPageSnapshot> {
    val pageState =
        when (route) {
          UserSubmissionSection.Gallery -> galleryRepository.loadGalleryPage(username, url)
          UserSubmissionSection.Favorites -> favoritesRepository.loadFavoritesPage(username, url)
          UserSubmissionSection.Journals ->
              ResourceResult.Error(
                  IllegalStateException("Unsupported route for submission adapter")
              )
        }
    return pageState.mapLoadedPage { page ->
      page.toLoadedPage(
          requestKey =
              url
                  ?: if (route == UserSubmissionSection.Gallery) galleryRootUrl(username)
                  else favoritesRootUrl(username),
          pageId = "${route.routeKey}:${url ?: "first"}",
          firstPageUrl =
              when (route) {
                UserSubmissionSection.Gallery ->
                    initialPageUrl?.trim().takeUnless { it.isNullOrBlank() }
                        ?: galleryRootUrl(username)
                UserSubmissionSection.Favorites -> favoritesRootUrl(username)
                UserSubmissionSection.Journals -> null
              },
          lastPageNumber = lastPageNumberOverride,
          lastPageUrl =
              if (route == UserSubmissionSection.Gallery && lastPageNumberOverride != null) {
                buildGalleryPageUrl(galleryFirstPageUrl(), lastPageNumberOverride)
              } else {
                null
              },
      )
    }
  }

  private suspend fun loadGalleryPageNumber(
      pageNumber: Int,
      lastPageNumberOverride: Int? = resolvedGalleryLastPageNumber,
  ): ResourceResult<SubmissionPageSnapshot> {
    val firstUrl = galleryFirstPageUrl()
    val targetUrl = if (pageNumber <= 1) firstUrl else buildGalleryPageUrl(firstUrl, pageNumber)
    return loadByUrl(targetUrl, lastPageNumberOverride = lastPageNumberOverride)
  }

  private suspend fun resolveGalleryLastPage(
      initialUpperBound: Int? = null
  ): ResourceResult<SubmissionPageSnapshot> {
    resolvedGalleryLastPageNumber?.let { lastPageNumber ->
      return resolveGalleryLastPageFromHint(lastPageNumber)
    }

    val firstPageResult = loadGalleryPageNumber(1, lastPageNumberOverride = null)
    val firstPage = (firstPageResult as? ResourceResult.Success)?.data ?: return firstPageResult
    if (firstPage.items.isEmpty()) {
      resolvedGalleryLastPageNumber = 1
      return ResourceResult.Success(firstPage.withResolvedLastPage(1, galleryFirstPageUrl()))
    }

    var lowPageNumber = 1
    var lowPage = firstPage.withResolvedLastPage(null, galleryFirstPageUrl())
    var highPageNumber = initialUpperBound?.coerceAtLeast(2)

    if (highPageNumber != null) {
      val initialUpperResult = loadGalleryPageNumber(highPageNumber, lastPageNumberOverride = null)
      val initialUpperPage =
          (initialUpperResult as? ResourceResult.Success)?.data ?: return initialUpperResult
      if (initialUpperPage.items.isEmpty()) {
        return finalizeGalleryLastPage(
            lowPageNumber = lowPageNumber,
            lowPage = lowPage,
            highPageNumber = highPageNumber,
        )
      }
      lowPageNumber = highPageNumber
      lowPage = initialUpperPage
      highPageNumber = null
    }

    var probePageNumber = (lowPageNumber * 2).coerceAtLeast(2)
    while (true) {
      val probeResult = loadGalleryPageNumber(probePageNumber, lastPageNumberOverride = null)
      val probePage = (probeResult as? ResourceResult.Success)?.data ?: return probeResult
      if (probePage.items.isEmpty()) {
        return finalizeGalleryLastPage(
            lowPageNumber = lowPageNumber,
            lowPage = lowPage,
            highPageNumber = probePageNumber,
        )
      }
      lowPageNumber = probePageNumber
      lowPage = probePage
      probePageNumber *= 2
    }
  }

  private suspend fun finalizeGalleryLastPage(
      lowPageNumber: Int,
      lowPage: SubmissionPageSnapshot,
      highPageNumber: Int,
  ): ResourceResult<SubmissionPageSnapshot> {
    val firstUrl = galleryFirstPageUrl()
    var low = lowPageNumber
    var lowLoadedPage = lowPage
    var high = highPageNumber
    while (low + 1 < high) {
      val mid = (low + high) / 2
      val midResult = loadGalleryPageNumber(mid, lastPageNumberOverride = null)
      val midPage = (midResult as? ResourceResult.Success)?.data ?: return midResult
      if (midPage.items.isEmpty()) {
        high = mid
      } else {
        low = mid
        lowLoadedPage = midPage
      }
    }
    resolvedGalleryLastPageNumber = low
    lastPageCacheKey?.let { cacheKey -> UserSubmissionLastPageHintCache.put(cacheKey, low) }
    return ResourceResult.Success(lowLoadedPage.withResolvedLastPage(low, firstUrl))
  }

  private suspend fun resolveGalleryLastPageFromHint(
      hintPageNumber: Int
  ): ResourceResult<SubmissionPageSnapshot> {
    val safeHintPageNumber = hintPageNumber.coerceAtLeast(1)
    val hintResult = loadGalleryPageNumber(safeHintPageNumber, lastPageNumberOverride = null)
    val hintPage = (hintResult as? ResourceResult.Success)?.data ?: return hintResult
    if (hintPage.items.isEmpty()) {
      return finalizeGalleryLastPage(
          lowPageNumber = 1,
          lowPage =
              loadGalleryPageNumber(1, lastPageNumberOverride = null).let { result ->
                (result as? ResourceResult.Success)?.data ?: return result
              },
          highPageNumber = safeHintPageNumber,
      )
    }
    if (hintPage.nextRequestKey == null) {
      resolvedGalleryLastPageNumber = safeHintPageNumber
      lastPageCacheKey?.let { cacheKey ->
        UserSubmissionLastPageHintCache.put(cacheKey, safeHintPageNumber)
      }
      return ResourceResult.Success(
          hintPage.withResolvedLastPage(safeHintPageNumber, galleryFirstPageUrl())
      )
    }
    var lowPageNumber = safeHintPageNumber
    var lowPage = hintPage
    var probeStep = 1
    while (true) {
      val probePageNumber = safeHintPageNumber + probeStep
      val probeResult = loadGalleryPageNumber(probePageNumber, lastPageNumberOverride = null)
      val probePage = (probeResult as? ResourceResult.Success)?.data ?: return probeResult
      if (probePage.items.isEmpty()) {
        return finalizeGalleryLastPage(
            lowPageNumber = lowPageNumber,
            lowPage = lowPage,
            highPageNumber = probePageNumber,
        )
      }
      if (probePage.nextRequestKey == null) {
        resolvedGalleryLastPageNumber = probePageNumber
        lastPageCacheKey?.let { cacheKey ->
          UserSubmissionLastPageHintCache.put(cacheKey, probePageNumber)
        }
        return ResourceResult.Success(
            probePage.withResolvedLastPage(probePageNumber, galleryFirstPageUrl())
        )
      }
      lowPageNumber = probePageNumber
      lowPage = probePage
      probeStep *= 2
    }
  }

  private fun galleryFirstPageUrl(): String =
      initialPageUrl?.trim().takeUnless { it.isNullOrBlank() } ?: galleryRootUrl(username)
}

private object UserSubmissionLastPageHintCache {
  private val values: MutableMap<String, Int> = mutableMapOf()

  fun get(key: String): Int? = values[key]

  fun put(key: String, value: Int) {
    values[key] = value
  }
}

internal class SeriesSubmissionSourceAdapter(
    private val repository: SubmissionSeriesSubmissionSource,
    private val series: SubmissionSeriesResolvedSeries,
) : SubmissionSourceAdapter() {
  private val resolver = SubmissionSeriesService(repository = repository)
  private val loadedUrls =
      series.seedSubmissions
          .map(SubmissionThumbnail::submissionUrl)
          .filter(String::isNotBlank)
          .map(::normalizeSubmissionSeriesUrl)
          .toMutableSet()
  private val orderedUrls = series.orderedSubmissionUrls.distinctBy(::normalizeSubmissionSeriesUrl)
  private val orderedIndexByUrl =
      orderedUrls.mapIndexed { index, url -> normalizeSubmissionSeriesUrl(url) to index }.toMap()

  override val sourceKind: SubmissionContextSourceKind = SubmissionContextSourceKind.SEQUENCE
  override val paginationModel: SubmissionPaginationModel =
      SubmissionPaginationModel(
          kind = SubmissionPaginationKind.CURSOR,
          hasFirstPage = true,
          knowsLastPage = false,
      )

  override suspend fun loadInitialPage(): ResourceResult<SubmissionPageSnapshot> =
      ResourceResult.Success(
          SubmissionPageSnapshot(
              pageId = "series:${series.candidateKey}:seed",
              requestKey = series.firstSubmissionUrl,
              items = series.seedSubmissions,
              previousRequestKey = series.previousRequestKey,
              nextRequestKey = series.nextRequestKey,
              firstRequestKey = series.firstSubmissionUrl,
              lastRequestKey = null,
              lastPageNumber = null,
              totalCount = null,
          )
      )

  override suspend fun loadNextPage(requestKey: String): ResourceResult<SubmissionPageSnapshot> =
      loadSeriesPage(requestKey)

  override suspend fun loadPreviousPage(
      requestKey: String
  ): ResourceResult<SubmissionPageSnapshot> = loadSeriesPage(requestKey)

  private suspend fun loadSeriesPage(requestKey: String): ResourceResult<SubmissionPageSnapshot> =
      when (val detailState = repository.loadSubmissionDetailByUrl(requestKey)) {
        is ResourceResult.Success -> ResourceResult.Success(detailState.data.toLoadedPage())
        is ResourceResult.AuthRequired ->
            ResourceResult.AuthRequired(detailState.requestUrl, detailState.message)
        ResourceResult.CfChallenge -> ResourceResult.CfChallenge
        is ResourceResult.MatureBlocked -> ResourceResult.MatureBlocked(detailState.reason)
        is ResourceResult.Error -> ResourceResult.Error(detailState.exception)
      }

  private fun Submission.toLoadedPage(): SubmissionPageSnapshot {
    val normalizedUrl = normalizeSubmissionSeriesUrl(submissionUrl)
    loadedUrls += normalizedUrl
    val previousRequestKey: String?
    val nextRequestKey: String?

    when (series.rule) {
      SubmissionSeriesRule.NUMBERED_BLOCKS -> {
        val currentIndex = orderedIndexByUrl[normalizedUrl]
        previousRequestKey =
            currentIndex
                ?.minus(1)
                ?.takeIf { index -> index >= 0 }
                ?.let(orderedUrls::get)
                ?.takeUnless { url -> normalizeSubmissionSeriesUrl(url) in loadedUrls }
        nextRequestKey =
            currentIndex
                ?.plus(1)
                ?.takeIf { index -> index < orderedUrls.size }
                ?.let(orderedUrls::get)
                ?.takeUnless { url -> normalizeSubmissionSeriesUrl(url) in loadedUrls }
      }

      SubmissionSeriesRule.PREV_NEXT_BLOCK -> {
        val candidate = resolver.detectCandidate(descriptionHtml, submissionUrl)
        previousRequestKey =
            candidate?.previousSubmissionUrl?.takeUnless { url ->
              normalizeSubmissionSeriesUrl(url) in loadedUrls
            }
        nextRequestKey =
            candidate?.nextSubmissionUrl?.takeUnless { url ->
              normalizeSubmissionSeriesUrl(url) in loadedUrls
            }
      }
    }

    return SubmissionPageSnapshot(
        pageId = "series:${series.candidateKey}:$normalizedUrl",
        requestKey = submissionUrl,
        items = listOf(toSubmissionThumbnail()),
        previousRequestKey = previousRequestKey,
        nextRequestKey = nextRequestKey,
        firstRequestKey = series.firstSubmissionUrl,
        lastRequestKey = null,
        lastPageNumber = null,
        totalCount = null,
    )
  }
}

internal class SeedSubmissionSourceAdapter(
    override val sourceKind: SubmissionContextSourceKind,
    private val items: List<SubmissionThumbnail>,
) : SubmissionSourceAdapter() {
  override val paginationModel: SubmissionPaginationModel =
      SubmissionPaginationModel(canJumpToCachedSubmission = true)

  override suspend fun loadInitialPage(): ResourceResult<SubmissionPageSnapshot> =
      ResourceResult.Success(
          SubmissionPageSnapshot(pageId = "seed:$sourceKind", requestKey = null, items = items)
      )
}

private fun FeedPage.toLoadedPage(requestKey: String?, pageId: String): SubmissionPageSnapshot =
    SubmissionPageSnapshot(
        pageId = pageId,
        requestKey = requestKey,
        items = submissions,
        previousRequestKey = previousPageUrl,
        nextRequestKey = nextPageUrl,
        firstRequestKey = firstPageUrl,
        lastRequestKey = lastPageUrl,
        lastPageNumber = null,
        totalCount = null,
    )

private fun SubmissionListingPage.toLoadedPage(
    requestKey: String,
    pageId: String,
    firstPageUrl: String?,
    lastPageNumber: Int?,
    lastPageUrl: String?,
): SubmissionPageSnapshot {
  val pageNumber = currentPageNumber ?: parseQueryParamInt(requestKey, "page") ?: 1
  return SubmissionPageSnapshot(
      pageId = pageId,
      requestKey = requestKey,
      items = submissions,
      pageNumber = pageNumber,
      previousRequestKey =
          if (pageNumber > 1) replaceQueryParam(requestKey, "page", (pageNumber - 1).toString())
          else null,
      nextRequestKey = nextPageUrl,
      firstRequestKey = firstPageUrl,
      lastRequestKey = lastPageUrl,
      lastPageNumber = lastPageNumber,
      totalCount = totalCount,
  )
}

private fun GalleryPage.toLoadedPage(
    requestKey: String,
    pageId: String,
    firstPageUrl: String?,
    lastPageNumber: Int?,
    lastPageUrl: String?,
): SubmissionPageSnapshot {
  val normalizedFirstPageUrl = firstPageUrl?.trim()?.removeSuffix("/")
  val normalizedRequestKey = requestKey.trim().removeSuffix("/")
  val pageNumber =
      currentPageNumber
          ?: parseGalleryPageNumber(requestKey)
          ?: parseQueryParamInt(requestKey, "page")
          ?: if (normalizedFirstPageUrl != null && normalizedRequestKey == normalizedFirstPageUrl) {
            1
          } else {
            null
          }
  return SubmissionPageSnapshot(
      pageId = pageId,
      requestKey = requestKey,
      items = submissions,
      pageNumber = pageNumber,
      previousRequestKey =
          if (pageNumber != null && pageNumber > 1 && firstPageUrl != null) {
            buildGalleryPageUrl(firstPageUrl, pageNumber - 1)
          } else {
            null
          },
      nextRequestKey = nextPageUrl,
      firstRequestKey = firstPageUrl,
      lastRequestKey = lastPageUrl,
      lastPageNumber = lastPageNumber,
      totalCount = null,
  )
}

private fun SubmissionPageSnapshot.withResolvedLastPage(
    lastPageNumber: Int?,
    firstPageUrl: String,
): SubmissionPageSnapshot =
    copy(
        lastPageNumber = lastPageNumber,
        lastRequestKey =
            lastPageNumber?.let { pageNumber ->
              if (pageNumber <= 1) firstPageUrl else buildGalleryPageUrl(firstPageUrl, pageNumber)
            },
    )

private inline fun <T> ResourceResult<T>.mapLoadedPage(
    transform: (T) -> SubmissionPageSnapshot
): ResourceResult<SubmissionPageSnapshot> =
    when (this) {
      is ResourceResult.Success -> ResourceResult.Success(transform(data))
      is ResourceResult.AuthRequired -> ResourceResult.AuthRequired(requestUrl, message)
      ResourceResult.CfChallenge -> ResourceResult.CfChallenge
      is ResourceResult.MatureBlocked -> ResourceResult.MatureBlocked(reason)
      is ResourceResult.Error -> ResourceResult.Error(exception)
    }

private fun replaceQueryParam(url: String, key: String, value: String): String {
  val trimmed = url.trim()
  if (trimmed.isBlank()) return trimmed
  val prefix = "${key.encodeURLParameter()}="
  val parts = trimmed.split("?", limit = 2)
  if (parts.size == 1) {
    return "$trimmed?$prefix${value.encodeURLParameter()}"
  }
  val base = parts[0]
  val queryParts =
      parts[1]
          .split("&")
          .filter { part -> part.isNotBlank() && !part.startsWith(prefix) }
          .toMutableList()
  queryParts += "$prefix${value.encodeURLParameter()}"
  return "$base?${queryParts.joinToString("&")}"
}

private fun parseQueryParamInt(url: String, key: String): Int? =
    Regex("""(?:\?|&)${Regex.escape(key)}=(\d+)""")
        .find(url)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

private fun parseGalleryPageNumber(url: String): Int? =
    Regex("""/(\d+)/?$""").find(url.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()

private fun buildGalleryPageUrl(firstPageUrl: String, pageNumber: Int): String {
  val normalized = firstPageUrl.trim().trimEnd('/')
  if (pageNumber <= 1) return "$normalized/"
  val base = Regex("""/\d+$""").replace(normalized, "")
  return "$base/$pageNumber/"
}

private fun galleryRootUrl(username: String): String = me.domino.fa2.utils.FaUrls.gallery(username)

private fun favoritesRootUrl(username: String): String =
    me.domino.fa2.utils.FaUrls.favorites(username)

private const val searchPageSize: Int = 72
private const val searchResultCountUpperBound: Int = 5000
