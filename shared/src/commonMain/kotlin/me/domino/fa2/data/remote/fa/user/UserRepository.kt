package me.domino.fa2.data.remote.fa.user

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.User
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.core.PageType
import me.domino.fa2.data.remote.fa.core.cacheJson
import me.domino.fa2.data.remote.fa.core.fetchParsed
import me.domino.fa2.data.remote.fa.social.SocialActionResult
import me.domino.fa2.data.remote.fa.social.SocialActionSource
import me.domino.fa2.utils.FaUrls

class UserRepository(
    private val html: FaHtmlDataSource,
    private val parser: UserParser,
    private val queries: PageQueryCache,
    private val actions: SocialActionSource,
) : WatchRecommendationUserSource {
  fun user(username: String): Flow<QueryState<User>> =
      queries.queryFlow(key(username), PageType.User, { cacheJson.encodeToString(it) }, ::decode) {
        fetch(username)
      }

  override suspend fun loadUser(username: String): ResourceResult<User> =
      queries.queryOnce(key(username), PageType.User, { cacheJson.encodeToString(it) }, ::decode) {
        fetch(username)
      }

  suspend fun refreshUser(username: String): ResourceResult<User> {
    queries.invalidate(key(username))
    return loadUser(username)
  }

  suspend fun toggleWatch(username: String, actionUrl: String): ResourceResult<Unit> {
    if (actionUrl.isBlank())
        return ResourceResult.Error(IllegalArgumentException("Missing action URL"))
    return when (val result = actions.execute(actionUrl.trim())) {
      is SocialActionResult.Completed -> {
        queries.invalidate(key(username))
        ResourceResult.Success(Unit)
      }
      is SocialActionResult.Challenge -> ResourceResult.CfChallenge
      is SocialActionResult.Blocked -> ResourceResult.MatureBlocked(result.reason)
      is SocialActionResult.Failed -> ResourceResult.Error(IllegalStateException(result.message))
    }
  }

  private suspend fun fetch(username: String) =
      html.fetchParsed(FaUrls.user(username)) { body, finalUrl -> parser.parse(body, finalUrl) }

  private fun key(username: String) = "user:username=${username.trim().lowercase()}"

  private fun decode(json: String) =
      runCatching { cacheJson.decodeFromString<User>(json) }.getOrNull()
}
