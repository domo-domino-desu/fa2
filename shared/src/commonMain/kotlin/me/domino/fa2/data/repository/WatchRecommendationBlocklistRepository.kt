package me.domino.fa2.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.store.storeJson

interface WatchRecommendationBlocklistRepository {
  suspend fun loadBlockedUsernameSet(): Set<String>

  suspend fun listBlockedUsernames(): List<String>

  suspend fun addBlockedUsername(username: String)

  suspend fun removeBlockedUsername(username: String)
}

class PersistedWatchRecommendationBlocklistRepository(
    private val keyValueStorage: KeyValueStorage,
) : WatchRecommendationBlocklistRepository {
  override suspend fun loadBlockedUsernameSet(): Set<String> {
    val snapshot = loadSnapshot()
    return snapshot.blockedUsernames.toSet()
  }

  override suspend fun listBlockedUsernames(): List<String> = loadSnapshot().blockedUsernames

  override suspend fun addBlockedUsername(username: String) {
    val normalized = normalizeUsername(username) ?: return
    val current = loadSnapshot()
    if (normalized in current.blockedUsernames) return
    val next =
        WatchRecommendationBlocklistSnapshot(
            blockedUsernames = current.blockedUsernames + normalized
        )
    keyValueStorage.save(KEY_WATCH_RECOMMENDATION_BLOCKLIST, storeJson.encodeToString(next))
  }

  override suspend fun removeBlockedUsername(username: String) {
    val normalized = normalizeUsername(username) ?: return
    val current = loadSnapshot()
    if (normalized !in current.blockedUsernames) return
    val next =
        WatchRecommendationBlocklistSnapshot(
            blockedUsernames = current.blockedUsernames.filterNot { it == normalized }
        )
    keyValueStorage.save(KEY_WATCH_RECOMMENDATION_BLOCKLIST, storeJson.encodeToString(next))
  }

  private suspend fun loadSnapshot(): WatchRecommendationBlocklistSnapshot {
    val raw = keyValueStorage.load(KEY_WATCH_RECOMMENDATION_BLOCKLIST)?.trim().orEmpty()
    if (raw.isBlank()) return WatchRecommendationBlocklistSnapshot()
    return runCatching { storeJson.decodeFromString<WatchRecommendationBlocklistSnapshot>(raw) }
        .getOrDefault(WatchRecommendationBlocklistSnapshot())
        .normalized()
  }

  private fun normalizeUsername(username: String): String? =
      username.trim().lowercase().takeIf { it.isNotBlank() }

  companion object {
    internal const val KEY_WATCH_RECOMMENDATION_BLOCKLIST: String =
        "watch_recommendation.blocklist.v1"
  }
}

@Serializable
data class WatchRecommendationBlocklistSnapshot(
    val blockedUsernames: List<String> = emptyList(),
) {
  fun normalized(): WatchRecommendationBlocklistSnapshot =
      copy(
          blockedUsernames =
              blockedUsernames
                  .asSequence()
                  .map { username -> username.trim().lowercase() }
                  .filter { username -> username.isNotBlank() }
                  .distinct()
                  .toList()
      )
}
