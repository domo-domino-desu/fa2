package me.domino.fa2.ui.pages.user.watchlist

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.data.repository.WatchlistRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.i18n.appString
import me.domino.fa2.i18n.appStringOrFallback
import me.domino.fa2.ui.state.PaginationReducer
import me.domino.fa2.ui.state.PaginationSnapshot
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState

private const val watchlistAutoLoadThreshold = 14

/** 用户关注列表状态。 */
data class UserWatchlistUiState(
    /** 当前页用户列表。 */
    val users: List<WatchlistUser> = emptyList(),
    /** 下一页地址。 */
    val nextPageUrl: String? = null,
    /** 是否加载中。 */
    val loading: Boolean = false,
    /** 是否刷新中。 */
    val refreshing: Boolean = false,
    /** 是否正在加载更多。 */
    val isLoadingMore: Boolean = false,
    /** 首页错误。 */
    val errorMessage: String? = null,
    /** 追加错误。 */
    val appendErrorMessage: String? = null,
    /** 是否正在随机打乱。 */
    val isShuffling: Boolean = false,
    /** 是否已缓存全量结果。 */
    val hasShuffledAllUsers: Boolean = false,
    /** 已缓存的全量结果。 */
    val shuffledAllUsers: List<WatchlistUser> = emptyList(),
    /** 随机打乱版本号，用于触发列表无动画回顶。 */
    val shuffleVersion: Int = 0,
) {
  /** 是否有下一页。 */
  val hasMore: Boolean
    get() = !nextPageUrl.isNullOrBlank()
}

/** 用户关注列表 ScreenModel。 */
class UserWatchlistScreenModel(
    private val username: String,
    private val category: WatchlistCategory,
    private val repository: WatchlistRepository,
    private val initialPageUrl: String? = null,
    private val settingsService: AppSettingsService? = null,
    private val systemLanguageProvider: SystemLanguageProvider? = null,
    private val random: Random = Random.Default,
    private val loadWatchlistPage:
        suspend (username: String, category: WatchlistCategory, nextPageUrl: String?) -> PageState<
                WatchlistPage
            > =
        repository::loadWatchlistPage,
    private val refreshWatchlistFirstPage:
        suspend (username: String, category: WatchlistCategory) -> PageState<WatchlistPage> =
        repository::refreshWatchlistFirstPage,
    private val loadAllWatchlistUsers:
        suspend (
            username: String,
            category: WatchlistCategory,
            useFreshFirstPage: Boolean,
        ) -> PageState<List<WatchlistUser>> =
        repository::loadAllWatchlistUsers,
) : StateScreenModel<UserWatchlistUiState>(UserWatchlistUiState()) {
  private val log = FaLog.withTag("UserWatchlistScreenModel")
  private val paginationStateMachine =
      PaginationReducer<WatchlistUser, String>(
          keyOf = { item -> item.username.lowercase() },
          challengeMessage = { appString(Res.string.cloudflare_challenge_title) },
          appendFallbackErrorMessage = { appString(Res.string.load_failed_please_retry) },
      )
  private var loadJob: Job? = null
  private var appendJob: Job? = null
  private var shuffleJob: Job? = null

  init {
    load()
  }

  /** 加载首页。 */
  fun load(forceRefresh: Boolean = false) {
    log.i { "加载Watchlist页 -> 开始(user=$username,category=$category,forceRefresh=$forceRefresh)" }
    if (shuffleJob?.isActive == true) {
      log.d { "加载Watchlist页 -> 跳过(随机打乱中)" }
      return
    }
    if (loadJob?.isActive == true) {
      log.d { "加载Watchlist页 -> 跳过(已有任务)" }
      return
    }
    val snapshot = state.value
    if (!forceRefresh && snapshot.users.isNotEmpty()) {
      log.d { "加载Watchlist页 -> 跳过(已有数据)" }
      return
    }

    mutableState.value =
        snapshot.applyPagination(
            paginationStateMachine.beginLoad(
                snapshot = snapshot.toPaginationSnapshot(),
                forceRefresh = forceRefresh,
            )
        )

    val firstPageUrl =
        if (forceRefresh) {
          null
        } else {
          initialPageUrl?.trim()?.takeIf { value -> value.isNotBlank() }
        }
    loadJob =
        screenModelScope.launch {
          val next =
              if (forceRefresh) {
                refreshWatchlistFirstPage(
                    username,
                    category,
                )
              } else {
                loadWatchlistPage(
                    username,
                    category,
                    firstPageUrl,
                )
              }
          val reduced =
              paginationStateMachine.reduceFirstPage(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = next,
                  itemsOf = { page -> page.users },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = state.value.applyPagination(reduced)
          mutableState.value =
              if (forceRefresh && next is PageState.Success) {
                updated.copy(
                    isShuffling = false,
                    hasShuffledAllUsers = false,
                    shuffledAllUsers = emptyList(),
                )
              } else {
                updated.copy(isShuffling = false)
              }
          when (next) {
            is PageState.Success -> {
              log.i {
                "加载Watchlist页 -> ${summarizePageState(next)}(count=${mutableState.value.users.size})"
              }
            }

            is PageState.AuthRequired -> log.w { "加载Watchlist页 -> 需要重新登录" }
            PageState.CfChallenge -> log.w { "加载Watchlist页 -> Cloudflare验证" }
            is PageState.MatureBlocked -> log.w { "加载Watchlist页 -> 受限(${next.reason})" }
            is PageState.Error -> log.e(next.exception) { "加载Watchlist页 -> 失败" }
            PageState.Loading -> log.d { "加载Watchlist页 -> 加载中" }
          }
        }
  }

  /** 列表触底回调。 */
  fun onLastVisibleIndexChanged(lastVisibleIndex: Int) {
    val snapshot = state.value
    if (snapshot.users.isEmpty()) return
    if (snapshot.isShuffling) {
      log.d { "自动加载Watchlist页 -> 跳过(随机打乱中)" }
      return
    }
    if (lastVisibleIndex > snapshot.users.lastIndex - watchlistAutoLoadThreshold) {
      loadMore(force = false)
    }
  }

  /** 手动重试加载更多。 */
  fun retryLoadMore() {
    loadMore(force = true)
  }

  private fun loadMore(force: Boolean) {
    val snapshot = state.value
    if (snapshot.isShuffling) {
      log.d { "自动加载Watchlist页 -> 跳过(随机打乱中)" }
      return
    }
    val nextUrl = snapshot.nextPageUrl ?: return
    if (appendJob?.isActive == true) {
      log.d { "自动加载Watchlist页 -> 跳过(已有追加任务)" }
      return
    }
    if (!paginationStateMachine.canLoadMore(snapshot.toPaginationSnapshot(), force = force)) {
      log.d { "自动加载Watchlist页 -> 跳过(条件未满足)" }
      return
    }
    log.d { "自动加载Watchlist页 -> 开始(force=$force)" }

    mutableState.value =
        snapshot.applyPagination(
            paginationStateMachine.beginAppend(snapshot.toPaginationSnapshot())
        )

    appendJob =
        screenModelScope.launch {
          val next =
              loadWatchlistPage(
                  username,
                  category,
                  nextUrl,
              )
          val reduced =
              paginationStateMachine.reduceAppend(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = next,
                  itemsOf = { page -> page.users },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = state.value.applyPagination(reduced)
          mutableState.value = updated
          when (next) {
            is PageState.Success -> {
              log.d { "自动加载Watchlist页 -> ${summarizePageState(next)}(count=${updated.users.size})" }
            }

            is PageState.AuthRequired -> log.w { "自动加载Watchlist页 -> 需要重新登录" }
            PageState.CfChallenge -> log.w { "自动加载Watchlist页 -> Cloudflare验证" }
            is PageState.MatureBlocked -> log.w { "自动加载Watchlist页 -> 受限(${next.reason})" }
            is PageState.Error -> log.e(next.exception) { "自动加载Watchlist页 -> 失败" }
            PageState.Loading -> log.d { "自动加载Watchlist页 -> 加载中" }
          }
        }
  }

  /** 拉取全部已关注并随机打乱。 */
  fun shuffleAllWatchingUsers() {
    if (category != WatchlistCategory.Watching) {
      log.d { "随机打乱已关注 -> 跳过(非Watching页)" }
      return
    }
    if (shuffleJob?.isActive == true) {
      log.d { "随机打乱已关注 -> 跳过(已有任务)" }
      return
    }
    if (loadJob?.isActive == true || appendJob?.isActive == true) {
      log.d { "随机打乱已关注 -> 跳过(分页任务进行中)" }
      return
    }
    val snapshot = state.value
    if (snapshot.hasShuffledAllUsers) {
      val reshuffled = snapshot.shuffledAllUsers.shuffled(random)
      mutableState.value =
          snapshot.copy(
              users = reshuffled,
              errorMessage = null,
              appendErrorMessage = null,
              shuffleVersion = snapshot.shuffleVersion + 1,
          )
      log.i { "随机打乱已关注 -> 本地重排(count=${reshuffled.size})" }
      return
    }

    mutableState.value =
        snapshot.copy(
            isShuffling = true,
            errorMessage = null,
            appendErrorMessage = null,
        )
    log.i { "随机打乱已关注 -> 开始(user=$username)" }
    shuffleJob =
        screenModelScope.launch {
          when (
              val next =
                  loadAllWatchlistUsers(
                      username,
                      category,
                      false,
                  )
          ) {
            is PageState.Success -> {
              val shuffled = next.data.shuffled(random)
              mutableState.value =
                  state.value.copy(
                      users = shuffled,
                      nextPageUrl = null,
                      loading = false,
                      refreshing = false,
                      isLoadingMore = false,
                      errorMessage = null,
                      appendErrorMessage = null,
                      isShuffling = false,
                      hasShuffledAllUsers = true,
                      shuffledAllUsers = next.data,
                      shuffleVersion = state.value.shuffleVersion + 1,
                  )
              log.i { "随机打乱已关注 -> 成功(count=${shuffled.size})" }
            }

            is PageState.AuthRequired -> {
              mutableState.value =
                  state.value.copy(
                      isShuffling = false,
                      errorMessage = next.message,
                  )
              log.w { "随机打乱已关注 -> 需要重新登录" }
            }

            PageState.CfChallenge -> {
              mutableState.value =
                  state.value.copy(
                      isShuffling = false,
                      errorMessage = appString(Res.string.cloudflare_challenge_title),
                  )
              log.w { "随机打乱已关注 -> Cloudflare验证" }
            }

            is PageState.MatureBlocked -> {
              mutableState.value =
                  state.value.copy(
                      isShuffling = false,
                      errorMessage = next.reason,
                  )
              log.w { "随机打乱已关注 -> 受限(${next.reason})" }
            }

            is PageState.Error -> {
              val detail =
                  next.exception.message?.takeIf { it.isNotBlank() } ?: next.exception.toString()
              mutableState.value =
                  state.value.copy(
                      isShuffling = false,
                      errorMessage =
                          appStringOrFallback(
                              Res.string.shuffle_following_failed,
                              fallback = { "Failed to shuffle following: $detail" },
                              detail,
                          ),
                  )
              log.e(next.exception) { "随机打乱已关注 -> 失败" }
            }

            PageState.Loading -> {
              mutableState.value = state.value.copy(isShuffling = false)
            }
          }
        }
  }
}

private fun UserWatchlistUiState.toPaginationSnapshot(): PaginationSnapshot<WatchlistUser> =
    PaginationSnapshot(
        items = users,
        nextPageUrl = nextPageUrl,
        loading = loading,
        refreshing = refreshing,
        isLoadingMore = isLoadingMore,
        errorMessage = errorMessage,
        appendErrorMessage = appendErrorMessage,
    )

private fun UserWatchlistUiState.applyPagination(
    snapshot: PaginationSnapshot<WatchlistUser>
): UserWatchlistUiState =
    copy(
        users = snapshot.items,
        nextPageUrl = snapshot.nextPageUrl,
        loading = snapshot.loading,
        refreshing = snapshot.refreshing,
        isLoadingMore = snapshot.isLoadingMore,
        errorMessage = snapshot.errorMessage,
        appendErrorMessage = snapshot.appendErrorMessage,
    )
