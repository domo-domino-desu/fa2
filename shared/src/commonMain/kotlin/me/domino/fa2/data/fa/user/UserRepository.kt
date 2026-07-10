package me.domino.fa2.data.fa.user

import me.domino.fa2.data.fa.social.SocialActionResult
import me.domino.fa2.data.fa.social.SocialActionSource
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.User
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** User 主页仓储。 */
class UserRepository(
    private val userStore: UserPageCache,
    private val socialActionEndpoint: SocialActionSource,
) : UserResourceRepository {
  private val log = FaLog.withTag("UserRepository")

  /** 加载用户头部信息。 */
  override suspend fun loadUser(username: String): ResourceResult<User> {
    log.d { "加载用户资料 -> user=$username" }
    val state = userStore.loadOnce(username)
    log.d { "加载用户资料 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 强制刷新用户头部信息。 */
  override suspend fun refreshUser(username: String): ResourceResult<User> {
    log.i { "刷新用户资料 -> user=$username" }
    userStore.invalidate(username)
    val state = loadUser(username)
    log.i { "刷新用户资料 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** Watch / Unwatch 用户。 */
  override suspend fun toggleWatch(username: String, actionUrl: String): ResourceResult<Unit> {
    val normalizedUrl = actionUrl.trim()
    if (normalizedUrl.isBlank()) {
      log.w { "关注操作 -> 缺少actionUrl" }
      return ResourceResult.Error(IllegalArgumentException("Missing watch action url"))
    }
    log.i { "关注操作 -> user=$username,url=${summarizeUrl(normalizedUrl)}" }

    val state =
        when (val response = socialActionEndpoint.execute(normalizedUrl)) {
          is SocialActionResult.Completed -> {
            userStore.invalidate(username)
            ResourceResult.Success(Unit)
          }

          is SocialActionResult.Challenge ->
              ResourceResult.Error(IllegalStateException("Cloudflare challenge unresolved"))

          is SocialActionResult.Blocked -> ResourceResult.MatureBlocked(response.reason)
          is SocialActionResult.Failed ->
              ResourceResult.Error(IllegalStateException(response.message))
        }
    log.i { "关注操作 -> ${summarizeResourceResult(state)}" }
    return state
  }
}
