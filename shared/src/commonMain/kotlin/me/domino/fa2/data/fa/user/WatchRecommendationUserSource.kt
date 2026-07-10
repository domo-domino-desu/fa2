package me.domino.fa2.data.fa.user

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.User

/** User resource contract required by watch-recommendation domain logic. */
interface WatchRecommendationUserSource {
  suspend fun loadUser(username: String): ResourceResult<User>
}
