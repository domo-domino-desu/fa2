package me.domino.fa2.ui.pages.user

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import me.domino.fa2.data.repository.UserRepository
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState

/** User 页面状态。 */
data class UserUiState(
    /** 目标用户名。 */
    val username: String,
    /** 头部数据。 */
    val header: User? = null,
    /** 是否正在加载。 */
    val loading: Boolean = false,
    /** 错误文案。 */
    val errorMessage: String? = null,
    /** 是否展开完整简介。 */
    val profileExpanded: Boolean = false,
    /** 是否正在执行 watch/unwatch。 */
    val watchUpdating: Boolean = false,
    /** 各子路由最后一次打开的 folder URL。 */
    val submissionRouteFolderUrls: Map<UserChildRoute, String> = emptyMap(),
)

/** User 页面状态模型。 */
class UserScreenModel(
    /** 目标用户名。 */
    private val username: String,
    /** User 仓储。 */
    private val repository: UserRepository,
    /** 初始子路由。 */
    initialChildRoute: UserChildRoute = UserChildRoute.Gallery,
    /** 初始 folder URL（可选）。 */
    initialFolderUrl: String? = null,
) :
    StateScreenModel<UserUiState>(
        UserUiState(
            username = username,
            loading = true,
            submissionRouteFolderUrls =
                buildInitialFolderUrls(
                    initialChildRoute = initialChildRoute,
                    initialFolderUrl = initialFolderUrl,
                ),
        )
    ) {
  private val log = FaLog.withTag("UserScreenModel")
  private var loadJob: Job? = null
  private var sharedTopScrollState: UserSharedTopScrollState = UserSharedTopScrollState.Inline()
  private val bodyScrollPositions: MutableMap<String, UserBodyScrollPosition> = mutableMapOf()
  private val submissionSectionSnapshots: MutableMap<String, UserSubmissionSectionUiState> =
      mutableMapOf()

  init {
    load()
  }

  /** 读取头部信息。 */
  fun load(forceRefresh: Boolean = false) {
    log.i { "加载用户 -> 开始(user=$username,forceRefresh=$forceRefresh)" }
    if (loadJob?.isActive == true && !forceRefresh) {
      log.d { "加载用户 -> 跳过(已有任务)" }
      return
    }
    loadJob?.cancel()
    val snapshot = state.value
    mutableState.value = snapshot.copy(loading = true, errorMessage = null, watchUpdating = false)

    loadJob =
        screenModelScope.launch {
          when (
              val next =
                  if (forceRefresh) {
                    repository.refreshUser(username)
                  } else {
                    repository.loadUser(username)
                  }
          ) {
            is PageState.Success -> {
              mutableState.value =
                  state.value.copy(
                      header = next.data,
                      loading = false,
                      errorMessage = null,
                  )
              log.i { "加载用户 -> ${summarizePageState(next)}" }
            }

            PageState.CfChallenge -> {
              mutableState.value =
                  state.value.copy(loading = false, errorMessage = "需要 Cloudflare 验证")
              log.w { "加载用户 -> Cloudflare验证" }
            }

            is PageState.MatureBlocked -> {
              mutableState.value = state.value.copy(loading = false, errorMessage = next.reason)
              log.w { "加载用户 -> 受限(${next.reason})" }
            }

            is PageState.Error -> {
              mutableState.value =
                  state.value.copy(
                      loading = false,
                      errorMessage = next.exception.message ?: next.exception.toString(),
                  )
              log.e(next.exception) { "加载用户 -> 失败" }
            }

            PageState.Loading -> Unit
          }
        }
  }

  /** 展开/收起简介。 */
  fun toggleProfileExpanded() {
    mutableState.value = state.value.copy(profileExpanded = !state.value.profileExpanded)
  }

  /** 读取某子路由当前记住的 folder URL。 */
  fun getSubmissionRouteFolderUrl(route: UserChildRoute): String? =
      state.value.submissionRouteFolderUrls[route]

  /** 更新某子路由当前 folder URL。 */
  fun setSubmissionRouteFolderUrl(route: UserChildRoute, folderUrl: String) {
    val normalized = folderUrl.trim()
    if (normalized.isBlank()) return
    val current = state.value.submissionRouteFolderUrls[route]
    if (current == normalized) return
    mutableState.value =
        state.value.copy(
            submissionRouteFolderUrls =
                state.value.submissionRouteFolderUrls + (route to normalized)
        )
  }

  internal fun getSharedTopScrollState(): UserSharedTopScrollState = sharedTopScrollState

  internal fun setSharedTopScrollState(position: UserSharedTopScrollState) {
    if (sharedTopScrollState == position) return
    sharedTopScrollState = position
  }

  internal fun getBodyScrollPosition(scrollKey: String): UserBodyScrollPosition? {
    val position = bodyScrollPositions[scrollKey]
    return position
  }

  internal fun setBodyScrollPosition(scrollKey: String, position: UserBodyScrollPosition) {
    val current = bodyScrollPositions[scrollKey]
    if (current == position) return
    bodyScrollPositions[scrollKey] = position
  }

  fun getSubmissionSectionSnapshot(cacheKey: String): UserSubmissionSectionUiState? {
    val snapshot = submissionSectionSnapshots[cacheKey]
    return snapshot
  }

  fun setSubmissionSectionSnapshot(cacheKey: String, snapshot: UserSubmissionSectionUiState) {
    val normalized = snapshot.copy(loading = false, refreshing = false, isLoadingMore = false)
    val current = submissionSectionSnapshots[cacheKey]
    if (current == normalized) return
    submissionSectionSnapshots[cacheKey] = normalized
  }

  /** Watch / Unwatch 用户（乐观更新）。 */
  fun toggleWatch() {
    val snapshot = state.value
    val header = snapshot.header ?: return
    if (snapshot.watchUpdating) return
    val actionUrl = header.watchActionUrl.trim()
    if (actionUrl.isBlank()) return
    log.i { "关注操作 -> 开始(user=$username,toWatch=${!header.isWatching})" }

    val optimisticHeader = header.copy(isWatching = !header.isWatching)
    mutableState.value =
        snapshot.copy(header = optimisticHeader, watchUpdating = true, errorMessage = null)

    screenModelScope.launch {
      when (val next = repository.toggleWatch(username = username, actionUrl = actionUrl)) {
        is PageState.Success -> {
          when (val refreshed = repository.loadUser(username)) {
            is PageState.Success -> {
              mutableState.value =
                  state.value.copy(
                      header = refreshed.data,
                      loading = false,
                      watchUpdating = false,
                      errorMessage = null,
                  )
              log.i { "关注操作 -> 成功(isWatching=${refreshed.data.isWatching})" }
            }

            PageState.CfChallenge -> {
              mutableState.value =
                  state.value.copy(
                      loading = false,
                      watchUpdating = false,
                      errorMessage = "操作已提交，但刷新需要 Cloudflare 验证",
                  )
              log.w { "关注操作 -> 已提交, 但刷新需要Cloudflare验证" }
            }

            is PageState.MatureBlocked -> {
              mutableState.value =
                  state.value.copy(
                      loading = false,
                      watchUpdating = false,
                      errorMessage = "操作已提交，但刷新失败：${refreshed.reason}",
                  )
              log.w { "关注操作 -> 已提交, 但刷新受限(${refreshed.reason})" }
            }

            is PageState.Error -> {
              mutableState.value =
                  state.value.copy(
                      loading = false,
                      watchUpdating = false,
                      errorMessage =
                          "操作已提交，但刷新失败：${refreshed.exception.message ?: refreshed.exception}",
                  )
              log.e(refreshed.exception) { "关注操作 -> 已提交, 但刷新失败" }
            }

            PageState.Loading -> Unit
          }
        }

        PageState.CfChallenge -> {
          mutableState.value =
              state.value.copy(
                  header = header,
                  watchUpdating = false,
                  errorMessage = "需要 Cloudflare 验证",
              )
          log.w { "关注操作 -> Cloudflare验证" }
        }

        is PageState.MatureBlocked -> {
          mutableState.value =
              state.value.copy(
                  header = header,
                  watchUpdating = false,
                  errorMessage = next.reason,
              )
          log.w { "关注操作 -> 受限(${next.reason})" }
        }

        is PageState.Error -> {
          mutableState.value =
              state.value.copy(
                  header = header,
                  watchUpdating = false,
                  errorMessage = next.exception.message ?: next.exception.toString(),
              )
          log.e(next.exception) { "关注操作 -> 失败" }
        }

        PageState.Loading -> Unit
      }
    }
  }

  private companion object {
    fun buildInitialFolderUrls(
        initialChildRoute: UserChildRoute,
        initialFolderUrl: String?,
    ): Map<UserChildRoute, String> {
      val normalized = initialFolderUrl?.trim()?.takeIf { it.isNotBlank() } ?: return emptyMap()
      if (!initialChildRoute.isSubmissionSection) return emptyMap()
      return mapOf(initialChildRoute to normalized)
    }
  }
}
