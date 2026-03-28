package me.domino.fa2.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.store.storeJson

interface WatchRecommendationCooldownRepository {
  suspend fun loadExcludedUsernames(): Set<String>

  suspend fun recordDisplayedRecommendations(usernames: List<String>)
}

class PersistedWatchRecommendationCooldownRepository(
    private val keyValueStorage: KeyValueStorage,
) : WatchRecommendationCooldownRepository {
  override suspend fun loadExcludedUsernames(): Set<String> {
    val snapshot = loadSnapshot()
    return snapshot.recentRecommendationBatches
        .asSequence()
        .flatten()
        .map { username -> username.trim().lowercase() }
        .filter { username -> username.isNotBlank() }
        .toSet()
  }

  override suspend fun recordDisplayedRecommendations(usernames: List<String>) {
    val normalized =
        usernames
            .asSequence()
            .map { username -> username.trim().lowercase() }
            .filter { username -> username.isNotBlank() }
            .distinct()
            .toList()
    if (normalized.isEmpty()) return

    val current = loadSnapshot()
    val next =
        WatchRecommendationCooldownSnapshot(
            recentRecommendationBatches =
                (current.recentRecommendationBatches + listOf(normalized)).takeLast(maxBatches)
        )
    keyValueStorage.save(KEY_WATCH_RECOMMENDATION_COOLDOWN, storeJson.encodeToString(next))
  }

  private suspend fun loadSnapshot(): WatchRecommendationCooldownSnapshot {
    val raw = keyValueStorage.load(KEY_WATCH_RECOMMENDATION_COOLDOWN)?.trim().orEmpty()
    if (raw.isBlank()) return WatchRecommendationCooldownSnapshot()
    return runCatching { storeJson.decodeFromString<WatchRecommendationCooldownSnapshot>(raw) }
        .getOrDefault(WatchRecommendationCooldownSnapshot())
        .normalized()
  }

  companion object {
    internal const val KEY_WATCH_RECOMMENDATION_COOLDOWN: String =
        "watch_recommendation.cooldown.v1"
    private const val maxBatches: Int = 3
  }
}

@Serializable
data class WatchRecommendationCooldownSnapshot(
    val recentRecommendationBatches: List<List<String>> = emptyList(),
) {
  fun normalized(): WatchRecommendationCooldownSnapshot =
      copy(
          recentRecommendationBatches =
              recentRecommendationBatches
                  .map { batch ->
                    batch
                        .asSequence()
                        .map { username -> username.trim().lowercase() }
                        .filter { username -> username.isNotBlank() }
                        .distinct()
                        .toList()
                  }
                  .filter { batch -> batch.isNotEmpty() }
                  .takeLast(3)
      )
}
