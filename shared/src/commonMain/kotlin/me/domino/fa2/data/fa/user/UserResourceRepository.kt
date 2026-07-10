package me.domino.fa2.data.fa.user

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.User

/** User resource operations consumed by UI features. */
interface UserResourceRepository : WatchRecommendationUserSource {
  override suspend fun loadUser(username: String): ResourceResult<User>

  suspend fun refreshUser(username: String): ResourceResult<User>

  suspend fun toggleWatch(username: String, actionUrl: String): ResourceResult<Unit>
}
