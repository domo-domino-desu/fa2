package me.domino.fa2.data.fa.user

import me.domino.fa2.data.fa.social.SocialActionEndpoint
import me.domino.fa2.data.fa.social.SocialActionResult
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import me.domino.fa2.data.model.summarizePageState
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** User 主页仓储。 */
open class UserRepository(
    private val userStore: UserPageCache,
    private val socialActionEndpoint: SocialActionEndpoint,
) : me.domino.fa2.domain.watchrecommendation.WatchRecommendationUserSource {
  private val log = FaLog.withTag("UserRepository")

  /** 加载用户头部信息。 */
  override suspend fun loadUser(username: String): PageState<User> {
    log.d { "加载用户资料 -> user=$username" }
    val state = userStore.loadOnce(username)
    log.d { "加载用户资料 -> ${summarizePageState(state)}" }
    return state
  }

  /** 强制刷新用户头部信息。 */
  open suspend fun refreshUser(username: String): PageState<User> {
    log.i { "刷新用户资料 -> user=$username" }
    userStore.invalidate(username)
    val state = loadUser(username)
    log.i { "刷新用户资料 -> ${summarizePageState(state)}" }
    return state
  }

  /** Watch / Unwatch 用户。 */
  open suspend fun toggleWatch(username: String, actionUrl: String): PageState<Unit> {
    val normalizedUrl = actionUrl.trim()
    if (normalizedUrl.isBlank()) {
      log.w { "关注操作 -> 缺少actionUrl" }
      return PageState.Error(IllegalArgumentException("Missing watch action url"))
    }
    log.i { "关注操作 -> user=$username,url=${summarizeUrl(normalizedUrl)}" }

    val state =
        when (val response = socialActionEndpoint.execute(normalizedUrl)) {
          is SocialActionResult.Completed -> {
            userStore.invalidate(username)
            PageState.Success(Unit)
          }

          is SocialActionResult.Challenge ->
              PageState.Error(IllegalStateException("Cloudflare challenge unresolved"))

          is SocialActionResult.Blocked -> PageState.MatureBlocked(response.reason)
          is SocialActionResult.Failed -> PageState.Error(IllegalStateException(response.message))
        }
    log.i { "关注操作 -> ${summarizePageState(state)}" }
    return state
  }
}
