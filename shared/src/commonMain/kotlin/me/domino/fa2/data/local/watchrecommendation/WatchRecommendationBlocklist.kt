package me.domino.fa2.data.local.watchrecommendation

/** Persistence-agnostic contract for usernames excluded from recommendations. */
interface WatchRecommendationBlocklist {
  suspend fun loadBlockedUsernameSet(): Set<String>

  suspend fun listBlockedUsernames(): List<String>

  suspend fun addBlockedUsername(username: String)

  suspend fun removeBlockedUsername(username: String)
}
