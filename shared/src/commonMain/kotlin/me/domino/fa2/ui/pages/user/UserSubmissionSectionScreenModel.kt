package me.domino.fa2.ui.pages.user

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.GalleryFolderGroup
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.model.errorMessageOrNull
import me.domino.fa2.data.model.errorOrNull
import me.domino.fa2.data.model.message
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.data.remote.fa.favorites.FavoritesRepository
import me.domino.fa2.data.remote.fa.gallery.GalleryRepository
import me.domino.fa2.ui.components.paging.reduceAppendResult
import me.domino.fa2.utils.logging.FaLog

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
          snapshot.copy(submissions = emptyList(), nextPageUrl = null)
        } else {
          snapshot
        }

    mutableState.value =
        effectiveSnapshot.copy(
            loading = effectiveSnapshot.submissions.isEmpty(),
            refreshing = forceRefresh && effectiveSnapshot.submissions.isNotEmpty(),
            errorMessage = null,
        )

    loadJob =
        screenModelScope.launch {
          val firstPageState =
              if (forceRefresh) {
                loadFirstPage(basePageUrl = basePageUrl)
              } else {
                loadPage(nextPageUrl = basePageUrl)
              }
          val updated =
              when (firstPageState) {
                is ResourceResult.Success ->
                    state.value.copy(
                        submissions = firstPageState.data.submissions,
                        nextPageUrl = firstPageState.data.nextPageUrl,
                        folderGroups = firstPageState.data.folderGroups,
                        loading = false,
                        refreshing = false,
                    )
                else ->
                    state.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = firstPageState.errorMessageOrNull(),
                    )
              }
          mutableState.value = updated
          when (val next = firstPageState) {
            is ResourceResult.Success -> {
              log.i {
                "加载用户投稿分区 -> ${summarizeResourceResult(next)}(count=${updated.submissions.size})"
              }
            }

            is ResourceResult.AuthRequired -> log.w { "加载用户投稿分区 -> 需要重新登录" }
            ResourceResult.CfChallenge -> log.w { "加载用户投稿分区 -> Cloudflare验证" }
            is ResourceResult.MatureBlocked -> log.w { "加载用户投稿分区 -> 受限(${next.reason})" }
            is ResourceResult.Error -> log.e(next.exception) { "加载用户投稿分区 -> 失败" }
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
    log.i { "打开文件夹 -> 开始(route=$route,url=${normalized.trim()})" }
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
    if (!force && snapshot.appendErrorMessage != null) {
      log.d { "自动加载用户投稿分区 -> 跳过(条件未满足)" }
      return
    }
    log.d { "自动加载用户投稿分区 -> 开始(route=$route,force=$force)" }

    mutableState.value = snapshot.copy(isLoadingMore = true, appendErrorMessage = null)

    appendJob =
        screenModelScope.launch {
          val next = loadPage(nextPageUrl = nextUrl)
          when (
              val outcome =
                  next.reduceAppendResult(
                      state.value.submissions,
                      { it.submissions },
                      { it.nextPageUrl },
                      keyOf = { it.id },
                  )
          ) {
            is ResourceResult.Success -> {
              mutableState.value =
                  state.value.copy(
                      submissions = outcome.data.items,
                      nextPageUrl = outcome.data.nextPageUrl,
                      isLoadingMore = false,
                  )
              log.d {
                "自动加载用户投稿分区 -> ${summarizeResourceResult(next)}(count=${outcome.data.items.size})"
              }
            }
            else -> {
              mutableState.value =
                  state.value.copy(
                      isLoadingMore = false,
                      appendErrorMessage = outcome.errorOrNull()?.message(),
                  )
              log.w { "自动加载用户投稿分区 -> ${summarizeResourceResult(next)}" }
            }
          }
        }
  }

  private suspend fun loadPage(nextPageUrl: String?): ResourceResult<GalleryPage> {
    return when (route) {
      UserChildRoute.Gallery -> galleryRepository.loadGalleryPage(username, nextPageUrl)
      UserChildRoute.Favorites -> favoritesRepository.loadFavoritesPage(username, nextPageUrl)
      UserChildRoute.Journals ->
          ResourceResult.Error(IllegalStateException("Invalid route for submissions: $route"))
    }
  }

  private suspend fun loadFirstPage(basePageUrl: String?): ResourceResult<GalleryPage> {
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
          ResourceResult.Error(IllegalStateException("Invalid route for submissions: $route"))
    }
  }
}
