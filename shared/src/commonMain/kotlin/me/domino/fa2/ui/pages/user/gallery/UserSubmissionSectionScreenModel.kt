package me.domino.fa2.ui.pages.user.gallery

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.GalleryFolderGroup
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.FavoritesRepository
import me.domino.fa2.data.repository.GalleryRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.i18n.appString
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.state.PaginationSnapshot
import me.domino.fa2.ui.state.PaginationStateMachine
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState
import me.domino.fa2.util.logging.summarizeUrl

private const val userAutoLoadThreshold = 10

/** User 投稿子页状态。 */
data class UserSubmissionSectionUiState(
    /** 当前列表。 */
    val submissions: List<SubmissionThumbnail> = emptyList(),
    /** 下一页 URL。 */
    val nextPageUrl: String? = null,
    /** 是否加载中。 */
    val loading: Boolean = false,
    /** 是否刷新中。 */
    val refreshing: Boolean = false,
    /** 是否正在加载更多。 */
    val isLoadingMore: Boolean = false,
    /** 错误文案。 */
    val errorMessage: String? = null,
    /** 追加错误文案。 */
    val appendErrorMessage: String? = null,
    /** 文件夹分组。 */
    val folderGroups: List<GalleryFolderGroup> = emptyList(),
) {
  /** 是否有下一页。 */
  val hasMore: Boolean
    get() = !nextPageUrl.isNullOrBlank()
}

internal fun UserSubmissionSectionUiState.prepareForFolderSwitch(
    folderUrl: String
): UserSubmissionSectionUiState =
    copy(
        submissions = emptyList(),
        nextPageUrl = null,
        folderGroups = folderGroups.withActiveFolder(folderUrl),
    )

internal fun List<GalleryFolderGroup>.withActiveFolder(
    folderUrl: String
): List<GalleryFolderGroup> {
  if (isEmpty()) return this
  var changed = false
  val updatedGroups = map { group ->
    var groupChanged = false
    val updatedFolders =
        group.folders.map { folder ->
          val shouldBeActive = folder.url == folderUrl
          if (folder.isActive == shouldBeActive) {
            folder
          } else {
            changed = true
            groupChanged = true
            folder.copy(isActive = shouldBeActive)
          }
        }
    if (groupChanged) {
      group.copy(folders = updatedFolders)
    } else {
      group
    }
  }
  return if (changed) updatedGroups else this
}

/** User 投稿子页状态模型（Gallery/Favorites/Scraps）。 */
class UserSubmissionSectionScreenModel(
    /** 用户名。 */
    private val username: String,
    /** 子路由。 */
    private val route: UserChildRoute,
    /** Gallery 仓储。 */
    private val galleryRepository: GalleryRepository,
    /** Favorites 仓储。 */
    private val favoritesRepository: FavoritesRepository,
    private val settingsService: AppSettingsService? = null,
    private val systemLanguageProvider: SystemLanguageProvider? = null,
    /** 初始文件夹 URL（可选）。 */
    initialFolderUrl: String? = null,
    /** 初始快照（可选）。 */
    initialSnapshot: UserSubmissionSectionUiState? = null,
) :
    StateScreenModel<UserSubmissionSectionUiState>(
        initialSnapshot?.copy(
            loading = false,
            refreshing = false,
            isLoadingMore = false,
            errorMessage = null,
            appendErrorMessage = null,
        ) ?: UserSubmissionSectionUiState()
    ) {
  private val log = FaLog.withTag("UserSubmissionSectionScreenModel")
  private val paginationStateMachine =
      PaginationStateMachine<SubmissionThumbnail, Int>(
          keyOf = { item -> item.id },
          challengeMessage = { appString(Res.string.cloudflare_challenge_title) },
          appendFallbackErrorMessage = { appString(Res.string.load_failed_please_retry) },
      )
  private var loadJob: Job? = null
  private var appendJob: Job? = null
  private var basePageUrlOverride: String? = initialFolderUrl?.trim()?.takeIf { it.isNotBlank() }

  init {
    if (state.value.submissions.isEmpty()) {
      load()
    }
  }

  /** 加载首页。 */
  fun load(forceRefresh: Boolean = false, clearExisting: Boolean = false) {
    log.i {
      "加载用户投稿分区 -> 开始(user=$username,route=$route,forceRefresh=$forceRefresh,clearExisting=$clearExisting)"
    }
    if (loadJob?.isActive == true && !forceRefresh) {
      log.d { "加载用户投稿分区 -> 跳过(已有任务)" }
      return
    }
    loadJob?.cancel()
    val snapshot = state.value
    if (!forceRefresh && !clearExisting && snapshot.submissions.isNotEmpty()) {
      log.d { "加载用户投稿分区 -> 跳过(已有数据)" }
      return
    }

    val basePageUrl = basePageUrlOverride
    val effectiveSnapshot =
        if (clearExisting) {
          snapshot.copy(
              submissions = emptyList(),
              nextPageUrl = null,
          )
        } else {
          snapshot
        }

    mutableState.value =
        effectiveSnapshot.applyPagination(
            paginationStateMachine.beginLoad(
                snapshot = effectiveSnapshot.toPaginationSnapshot(),
                forceRefresh = forceRefresh,
            )
        )

    loadJob =
        screenModelScope.launch {
          val firstPageState =
              if (forceRefresh) {
                loadFirstPage(basePageUrl = basePageUrl)
              } else {
                loadPage(nextPageUrl = basePageUrl)
              }
          val reduced =
              paginationStateMachine.reduceFirstPage(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = firstPageState,
                  itemsOf = { page -> page.submissions },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          var updated = state.value.applyPagination(reduced)
          if (firstPageState is PageState.Success) {
            updated = updated.copy(folderGroups = firstPageState.data.folderGroups)
          }
          mutableState.value = updated
          when (val next = firstPageState) {
            is PageState.Success -> {
              log.i { "加载用户投稿分区 -> ${summarizePageState(next)}(count=${updated.submissions.size})" }
            }

            PageState.CfChallenge -> log.w { "加载用户投稿分区 -> Cloudflare验证" }
            is PageState.MatureBlocked -> log.w { "加载用户投稿分区 -> 受限(${next.reason})" }
            is PageState.Error -> log.e(next.exception) { "加载用户投稿分区 -> 失败" }
            PageState.Loading -> log.d { "加载用户投稿分区 -> 加载中" }
          }
        }
  }

  /** 触底回调。 */
  fun onLastVisibleIndexChanged(lastVisibleIndex: Int) {
    val snapshot = state.value
    if (snapshot.submissions.isEmpty()) return
    log.d {
      "自动加载用户投稿分区 -> 触发检查(route=$route,last=$lastVisibleIndex,total=${snapshot.submissions.size})"
    }
    if (lastVisibleIndex > snapshot.submissions.lastIndex - userAutoLoadThreshold) {
      loadMore(force = false)
    }
  }

  /** 手动重试加载更多。 */
  fun retryLoadMore() {
    loadMore(force = true)
  }

  /** 打开指定文件夹。 */
  fun openFolder(folderUrl: String) {
    val normalized =
        folderUrl.trim().ifBlank {
          return
        }
    log.i { "打开文件夹 -> 开始(route=$route,url=${summarizeUrl(normalized)})" }
    mutableState.value = state.value.prepareForFolderSwitch(normalized)
    basePageUrlOverride = normalized
    load(forceRefresh = true, clearExisting = true)
  }

  private fun loadMore(force: Boolean) {
    val snapshot = state.value
    val nextUrl = snapshot.nextPageUrl ?: return
    if (appendJob?.isActive == true) {
      log.d { "自动加载用户投稿分区 -> 跳过(已有追加任务)" }
      return
    }
    if (!paginationStateMachine.canLoadMore(snapshot.toPaginationSnapshot(), force = force)) {
      log.d { "自动加载用户投稿分区 -> 跳过(条件未满足)" }
      return
    }
    log.d { "自动加载用户投稿分区 -> 开始(route=$route,force=$force)" }

    mutableState.value =
        snapshot.applyPagination(
            paginationStateMachine.beginAppend(snapshot.toPaginationSnapshot())
        )

    appendJob =
        screenModelScope.launch {
          val next = loadPage(nextPageUrl = nextUrl)
          val reduced =
              paginationStateMachine.reduceAppend(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = next,
                  itemsOf = { page -> page.submissions },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = state.value.applyPagination(reduced)
          mutableState.value = updated
          when (next) {
            is PageState.Success -> {
              log.d {
                "自动加载用户投稿分区 -> ${summarizePageState(next)}(count=${updated.submissions.size})"
              }
            }

            PageState.CfChallenge -> log.w { "自动加载用户投稿分区 -> Cloudflare验证" }
            is PageState.MatureBlocked -> log.w { "自动加载用户投稿分区 -> 受限(${next.reason})" }
            is PageState.Error -> log.e(next.exception) { "自动加载用户投稿分区 -> 失败" }
            PageState.Loading -> log.d { "自动加载用户投稿分区 -> 加载中" }
          }
        }
  }

  private suspend fun loadPage(nextPageUrl: String?): PageState<GalleryPage> {
    return when (route) {
      UserChildRoute.Gallery -> galleryRepository.loadGalleryPage(username, nextPageUrl)
      UserChildRoute.Favorites -> favoritesRepository.loadFavoritesPage(username, nextPageUrl)
      UserChildRoute.Journals ->
          PageState.Error(IllegalStateException("Invalid route for submissions: $route"))
    }
  }

  private suspend fun loadFirstPage(basePageUrl: String?): PageState<GalleryPage> {
    return when (route) {
      UserChildRoute.Gallery ->
          galleryRepository.refreshGalleryFirstPage(
              username = username,
              firstPageUrlOverride = basePageUrl,
          )

      UserChildRoute.Favorites ->
          favoritesRepository.refreshFavoritesFirstPage(
              username = username,
              firstPageUrlOverride = basePageUrl,
          )

      UserChildRoute.Journals ->
          PageState.Error(IllegalStateException("Invalid route for submissions: $route"))
    }
  }
}

private fun UserSubmissionSectionUiState.toPaginationSnapshot():
    PaginationSnapshot<SubmissionThumbnail> =
    PaginationSnapshot(
        items = submissions,
        nextPageUrl = nextPageUrl,
        loading = loading,
        refreshing = refreshing,
        isLoadingMore = isLoadingMore,
        errorMessage = errorMessage,
        appendErrorMessage = appendErrorMessage,
    )

private fun UserSubmissionSectionUiState.applyPagination(
    snapshot: PaginationSnapshot<SubmissionThumbnail>
): UserSubmissionSectionUiState =
    copy(
        submissions = snapshot.items,
        nextPageUrl = snapshot.nextPageUrl,
        loading = snapshot.loading,
        refreshing = snapshot.refreshing,
        isLoadingMore = snapshot.isLoadingMore,
        errorMessage = snapshot.errorMessage,
        appendErrorMessage = snapshot.appendErrorMessage,
    )
