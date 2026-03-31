package me.domino.fa2.ui.pages.submission

import io.ktor.http.encodeURLParameter
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolver
import me.domino.fa2.application.submissionseries.SubmissionSeriesRule
import me.domino.fa2.application.submissionseries.normalizeSubmissionSeriesUrl
import me.domino.fa2.application.submissionseries.toSubmissionThumbnail
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.BrowseRepository
import me.domino.fa2.data.repository.FavoritesRepository
import me.domino.fa2.data.repository.FeedRepository
import me.domino.fa2.data.repository.GalleryRepository
import me.domino.fa2.data.repository.SearchRepository
import me.domino.fa2.data.repository.SubmissionDetailRepository
import me.domino.fa2.ui.pages.user.route.UserChildRoute

internal interface SubmissionSourceAdapter {
  val sourceKind: SubmissionContextSourceKind
  val paginationModel: SubmissionPaginationModel

  suspend fun loadInitialPage(): PageState<SubmissionLoadedPage>

  suspend fun loadNextPage(requestKey: String): PageState<SubmissionLoadedPage> =
      PageState.Error(UnsupportedOperationException("Next page is unsupported for $sourceKind"))

  suspend fun loadPreviousPage(requestKey: String): PageState<SubmissionLoadedPage> =
      PageState.Error(UnsupportedOperationException("Previous page is unsupported for $sourceKind"))

  suspend fun loadFirstPage(): PageState<SubmissionLoadedPage> =
      PageState.Error(UnsupportedOperationException("First page is unsupported for $sourceKind"))

  suspend fun loadLastPage(): PageState<SubmissionLoadedPage> =
      PageState.Error(UnsupportedOperationException("Last page is unsupported for $sourceKind"))

  suspend fun jumpToPage(pageNumber: Int): PageState<SubmissionLoadedPage> =
      PageState.Error(UnsupportedOperationException("Jump page is unsupported for $sourceKind"))
}

internal class FeedSubmissionSourceAdapter(
    private val repository: FeedRepository,
) : SubmissionSourceAdapter {
  override val sourceKind: SubmissionContextSourceKind = SubmissionContextSourceKind.FEED
  override val paginationModel: SubmissionPaginationModel =
      SubmissionPaginationModel(
          kind = SubmissionPaginationKind.CURSOR,
          hasFirstPage = true,
          knowsLastPage = false,
      )

  override suspend fun loadInitialPage(): PageState<SubmissionLoadedPage> =
      repository.loadFirstPage().mapLoadedPage { page ->
        page.toLoadedPage(
            requestKey = page.currentPageUrl,
            pageId = page.currentPageUrl ?: "feed:first",
        )
      }

  override suspend fun loadNextPage(requestKey: String): PageState<SubmissionLoadedPage> =
      repository.loadPageByUrl(requestKey).mapLoadedPage { page ->
        page.toLoadedPage(requestKey = requestKey, pageId = requestKey)
      }

  override suspend fun loadPreviousPage(requestKey: String): PageState<SubmissionLoadedPage> =
      repository.loadPageByUrl(requestKey).mapLoadedPage { page ->
        page.toLoadedPage(requestKey = requestKey, pageId = requestKey)
      }

  override suspend fun loadFirstPage(): PageState<SubmissionLoadedPage> =
      repository.loadPageByUrl(me.domino.fa2.util.FaUrls.submissions()).mapLoadedPage { page ->
        page.toLoadedPage(
            requestKey = page.currentPageUrl ?: me.domino.fa2.util.FaUrls.submissions(),
            pageId = page.currentPageUrl ?: "feed:first",
        )
      }
}

internal class SearchSubmissionSourceAdapter(
    private val repository: SearchRepository,
    private val firstPageUrl: String,
) : SubmissionSourceAdapter {
  override val sourceKind: SubmissionContextSourceKind = SubmissionContextSourceKind.SEARCH
  override val paginationModel: SubmissionPaginationModel =
      SubmissionPaginationModel(
          kind = SubmissionPaginationKind.NUMBERED,
          hasFirstPage = true,
          knowsLastPage = true,
      )

  override suspend fun loadInitialPage(): PageState<SubmissionLoadedPage> = loadByUrl(firstPageUrl)

  override suspend fun loadNextPage(requestKey: String): PageState<SubmissionLoadedPage> =
      loadByUrl(requestKey)

  override suspend fun loadPreviousPage(requestKey: String): PageState<SubmissionLoadedPage> =
      loadByUrl(requestKey)

  override suspend fun loadFirstPage(): PageState<SubmissionLoadedPage> = loadByUrl(firstPageUrl)

  override suspend fun loadLastPage(): PageState<SubmissionLoadedPage> {
    val first = loadInitialPage()
    val page = (first as? PageState.Success)?.data ?: return first
    val totalCount = page.totalCount ?: return PageState.Error(IllegalStateException("No total"))
    val lastPageNumber = ((minOf(totalCount, searchResultCountUpperBound) - 1) / searchPageSize) + 1
    return jumpToPage(lastPageNumber)
  }

  override suspend fun jumpToPage(pageNumber: Int): PageState<SubmissionLoadedPage> =
      loadByUrl(replaceQueryParam(firstPageUrl, "page", pageNumber.toString()))

  private suspend fun loadByUrl(url: String): PageState<SubmissionLoadedPage> =
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
) : SubmissionSourceAdapter {
  override val sourceKind: SubmissionContextSourceKind = SubmissionContextSourceKind.BROWSE
  override val paginationModel: SubmissionPaginationModel =
      SubmissionPaginationModel(
          kind = SubmissionPaginationKind.NUMBERED,
          hasFirstPage = true,
          knowsLastPage = false,
      )

  override suspend fun loadInitialPage(): PageState<SubmissionLoadedPage> = loadByUrl(firstPageUrl)

  override suspend fun loadNextPage(requestKey: String): PageState<SubmissionLoadedPage> =
      loadByUrl(requestKey)

  override suspend fun loadPreviousPage(requestKey: String): PageState<SubmissionLoadedPage> =
      loadByUrl(requestKey)

  override suspend fun loadFirstPage(): PageState<SubmissionLoadedPage> = loadByUrl(firstPageUrl)

  override suspend fun jumpToPage(pageNumber: Int): PageState<SubmissionLoadedPage> =
      loadByUrl(replaceQueryParam(firstPageUrl, "page", pageNumber.toString()))

  private suspend fun loadByUrl(url: String): PageState<SubmissionLoadedPage> =
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
    private val route: UserChildRoute,
    private val username: String,
    private val initialPageUrl: String?,
    private val galleryRepository: GalleryRepository,
    private val favoritesRepository: FavoritesRepository,
) : SubmissionSourceAdapter {
  private val lastPageCacheKey: String? =
      if (route == UserChildRoute.Gallery) {
        listOf(
                route.routeKey,
                username.lowercase(),
                initialPageUrl?.trim().orEmpty(),
            )
            .joinToString(separator = "|")
      } else {
        null
      }
  private var resolvedGalleryLastPageNumber: Int? =
      lastPageCacheKey?.let(UserSubmissionLastPageHintCache::get)

  override val sourceKind: SubmissionContextSourceKind =
      when (route) {
        UserChildRoute.Gallery -> SubmissionContextSourceKind.GALLERY
        UserChildRoute.Favorites -> SubmissionContextSourceKind.FAVORITES
        UserChildRoute.Journals -> SubmissionContextSourceKind.GALLERY
      }

  override val paginationModel: SubmissionPaginationModel =
      when (route) {
        UserChildRoute.Gallery ->
            SubmissionPaginationModel(
                kind = SubmissionPaginationKind.NUMBERED,
                hasFirstPage = true,
                knowsLastPage = true,
            )

        UserChildRoute.Favorites ->
            SubmissionPaginationModel(
                kind = SubmissionPaginationKind.CURSOR,
                hasFirstPage = true,
                knowsLastPage = false,
            )

        UserChildRoute.Journals -> SubmissionPaginationModel()
      }

  override suspend fun loadInitialPage(): PageState<SubmissionLoadedPage> =
      loadByUrl(initialPageUrl)

  override suspend fun loadNextPage(requestKey: String): PageState<SubmissionLoadedPage> =
      loadByUrl(requestKey)

  override suspend fun loadPreviousPage(requestKey: String): PageState<SubmissionLoadedPage> =
      if (route == UserChildRoute.Journals) {
        PageState.Error(
            UnsupportedOperationException("Previous page is unsupported for $sourceKind")
        )
      } else {
        loadByUrl(requestKey)
      }

  override suspend fun loadFirstPage(): PageState<SubmissionLoadedPage> =
      loadByUrl(
          when (route) {
            UserChildRoute.Gallery -> null
            UserChildRoute.Favorites -> null
            UserChildRoute.Journals -> initialPageUrl
          }
      )

  override suspend fun jumpToPage(pageNumber: Int): PageState<SubmissionLoadedPage> {
    if (route != UserChildRoute.Gallery) {
      return PageState.Error(
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
    if (targetPageNumber <= 1 || route != UserChildRoute.Gallery) return result
    val loadedPage = (result as? PageState.Success)?.data ?: return result
    return if (loadedPage.items.isEmpty()) {
      resolveGalleryLastPage(initialUpperBound = targetPageNumber)
    } else {
      result
    }
  }

  override suspend fun loadLastPage(): PageState<SubmissionLoadedPage> {
    if (route != UserChildRoute.Gallery) {
      return PageState.Error(
          UnsupportedOperationException("Last page is unsupported for $sourceKind")
      )
    }
    return resolveGalleryLastPage()
  }

  private suspend fun loadByUrl(
      url: String?,
      lastPageNumberOverride: Int? = resolvedGalleryLastPageNumber,
  ): PageState<SubmissionLoadedPage> {
    val pageState =
        when (route) {
          UserChildRoute.Gallery -> galleryRepository.loadGalleryPage(username, url)
          UserChildRoute.Favorites -> favoritesRepository.loadFavoritesPage(username, url)
          UserChildRoute.Journals ->
              PageState.Error(IllegalStateException("Unsupported route for submission adapter"))
        }
    return pageState.mapLoadedPage { page ->
      page.toLoadedPage(
          requestKey =
              url
                  ?: if (route == UserChildRoute.Gallery) galleryRootUrl(username)
                  else favoritesRootUrl(username),
          pageId = "${route.routeKey}:${url ?: "first"}",
          firstPageUrl =
              when (route) {
                UserChildRoute.Gallery ->
                    initialPageUrl?.trim().takeUnless { it.isNullOrBlank() }
                        ?: galleryRootUrl(username)
                UserChildRoute.Favorites -> favoritesRootUrl(username)
                UserChildRoute.Journals -> null
              },
          lastPageNumber = lastPageNumberOverride,
          lastPageUrl =
              if (route == UserChildRoute.Gallery && lastPageNumberOverride != null) {
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
  ): PageState<SubmissionLoadedPage> {
    val firstUrl = galleryFirstPageUrl()
    val targetUrl = if (pageNumber <= 1) firstUrl else buildGalleryPageUrl(firstUrl, pageNumber)
    return loadByUrl(targetUrl, lastPageNumberOverride = lastPageNumberOverride)
  }

  private suspend fun resolveGalleryLastPage(
      initialUpperBound: Int? = null,
  ): PageState<SubmissionLoadedPage> {
    resolvedGalleryLastPageNumber?.let { lastPageNumber ->
      return resolveGalleryLastPageFromHint(lastPageNumber)
    }

    val firstPageResult = loadGalleryPageNumber(1, lastPageNumberOverride = null)
    val firstPage = (firstPageResult as? PageState.Success)?.data ?: return firstPageResult
    if (firstPage.items.isEmpty()) {
      resolvedGalleryLastPageNumber = 1
      return PageState.Success(firstPage.withResolvedLastPage(1, galleryFirstPageUrl()))
    }

    var lowPageNumber = 1
    var lowPage = firstPage.withResolvedLastPage(null, galleryFirstPageUrl())
    var highPageNumber = initialUpperBound?.coerceAtLeast(2)

    if (highPageNumber != null) {
      val initialUpperResult = loadGalleryPageNumber(highPageNumber, lastPageNumberOverride = null)
      val initialUpperPage =
          (initialUpperResult as? PageState.Success)?.data ?: return initialUpperResult
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
      val probePage = (probeResult as? PageState.Success)?.data ?: return probeResult
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
      lowPage: SubmissionLoadedPage,
      highPageNumber: Int,
  ): PageState<SubmissionLoadedPage> {
    val firstUrl = galleryFirstPageUrl()
    var low = lowPageNumber
    var lowLoadedPage = lowPage
    var high = highPageNumber
    while (low + 1 < high) {
      val mid = (low + high) / 2
      val midResult = loadGalleryPageNumber(mid, lastPageNumberOverride = null)
      val midPage = (midResult as? PageState.Success)?.data ?: return midResult
      if (midPage.items.isEmpty()) {
        high = mid
      } else {
        low = mid
        lowLoadedPage = midPage
      }
    }
    resolvedGalleryLastPageNumber = low
    lastPageCacheKey?.let { cacheKey -> UserSubmissionLastPageHintCache.put(cacheKey, low) }
    return PageState.Success(lowLoadedPage.withResolvedLastPage(low, firstUrl))
  }

  private suspend fun resolveGalleryLastPageFromHint(
      hintPageNumber: Int,
  ): PageState<SubmissionLoadedPage> {
    val safeHintPageNumber = hintPageNumber.coerceAtLeast(1)
    val hintResult = loadGalleryPageNumber(safeHintPageNumber, lastPageNumberOverride = null)
    val hintPage = (hintResult as? PageState.Success)?.data ?: return hintResult
    if (hintPage.items.isEmpty()) {
      return finalizeGalleryLastPage(
          lowPageNumber = 1,
          lowPage =
              loadGalleryPageNumber(1, lastPageNumberOverride = null).let { result ->
                (result as? PageState.Success)?.data ?: return result
              },
          highPageNumber = safeHintPageNumber,
      )
    }
    if (hintPage.nextRequestKey == null) {
      resolvedGalleryLastPageNumber = safeHintPageNumber
      lastPageCacheKey?.let { cacheKey ->
        UserSubmissionLastPageHintCache.put(cacheKey, safeHintPageNumber)
      }
      return PageState.Success(
          hintPage.withResolvedLastPage(safeHintPageNumber, galleryFirstPageUrl())
      )
    }
    var lowPageNumber = safeHintPageNumber
    var lowPage = hintPage
    var probeStep = 1
    while (true) {
      val probePageNumber = safeHintPageNumber + probeStep
      val probeResult = loadGalleryPageNumber(probePageNumber, lastPageNumberOverride = null)
      val probePage = (probeResult as? PageState.Success)?.data ?: return probeResult
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
        return PageState.Success(
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
    private val repository: SubmissionDetailRepository,
    private val series: SubmissionSeriesResolvedSeries,
) : SubmissionSourceAdapter {
  private val resolver = SubmissionSeriesResolver(repository = repository)
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

  override suspend fun loadInitialPage(): PageState<SubmissionLoadedPage> =
      PageState.Success(
          SubmissionLoadedPage(
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

  override suspend fun loadNextPage(requestKey: String): PageState<SubmissionLoadedPage> =
      loadSeriesPage(requestKey)

  override suspend fun loadPreviousPage(requestKey: String): PageState<SubmissionLoadedPage> =
      loadSeriesPage(requestKey)

  private suspend fun loadSeriesPage(requestKey: String): PageState<SubmissionLoadedPage> =
      when (val detailState = repository.loadSubmissionDetailByUrl(requestKey)) {
        is PageState.Success -> PageState.Success(detailState.data.toLoadedPage())
        is PageState.AuthRequired ->
            PageState.AuthRequired(detailState.requestUrl, detailState.message)
        PageState.CfChallenge -> PageState.CfChallenge
        is PageState.MatureBlocked -> PageState.MatureBlocked(detailState.reason)
        is PageState.Error -> PageState.Error(detailState.exception)
        PageState.Loading -> PageState.Loading
      }

  private fun Submission.toLoadedPage(): SubmissionLoadedPage {
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

    return SubmissionLoadedPage(
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
) : SubmissionSourceAdapter {
  override val paginationModel: SubmissionPaginationModel =
      SubmissionPaginationModel(
          canJumpToCachedSubmission = true,
      )

  override suspend fun loadInitialPage(): PageState<SubmissionLoadedPage> =
      PageState.Success(
          SubmissionLoadedPage(
              pageId = "seed:$sourceKind",
              requestKey = null,
              items = items,
          )
      )
}

private fun FeedPage.toLoadedPage(requestKey: String?, pageId: String): SubmissionLoadedPage =
    SubmissionLoadedPage(
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
): SubmissionLoadedPage {
  val pageNumber = currentPageNumber ?: parseQueryParamInt(requestKey, "page") ?: 1
  return SubmissionLoadedPage(
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
): SubmissionLoadedPage {
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
  return SubmissionLoadedPage(
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

private fun SubmissionLoadedPage.withResolvedLastPage(
    lastPageNumber: Int?,
    firstPageUrl: String,
): SubmissionLoadedPage =
    copy(
        lastPageNumber = lastPageNumber,
        lastRequestKey =
            lastPageNumber?.let { pageNumber ->
              if (pageNumber <= 1) firstPageUrl else buildGalleryPageUrl(firstPageUrl, pageNumber)
            },
    )

private inline fun <T> PageState<T>.mapLoadedPage(
    transform: (T) -> SubmissionLoadedPage
): PageState<SubmissionLoadedPage> =
    when (this) {
      is PageState.Success -> PageState.Success(transform(data))
      is PageState.AuthRequired -> PageState.AuthRequired(requestUrl, message)
      PageState.CfChallenge -> PageState.CfChallenge
      is PageState.MatureBlocked -> PageState.MatureBlocked(reason)
      is PageState.Error -> PageState.Error(exception)
      PageState.Loading -> PageState.Loading
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

private fun galleryRootUrl(username: String): String = me.domino.fa2.util.FaUrls.gallery(username)

private fun favoritesRootUrl(username: String): String =
    me.domino.fa2.util.FaUrls.favorites(username)

private const val searchPageSize: Int = 72
private const val searchResultCountUpperBound: Int = 5000
