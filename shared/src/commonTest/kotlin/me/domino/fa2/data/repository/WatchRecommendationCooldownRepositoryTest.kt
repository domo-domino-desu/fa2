package me.domino.fa2.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.KeyValueStorage
import okio.FileSystem
import okio.Path.Companion.toPath

class WatchRecommendationCooldownRepositoryTest {
  @Test
  fun returnsEmptyWhenStorageIsBlank() = runTest {
    val repository = buildRepository()

    assertEquals(emptySet(), repository.loadExcludedUsernames())
  }

  @Test
  fun keepsOnlyRecentThreeBatches() = runTest {
    val repository = buildRepository()

    repository.recordDisplayedRecommendations(listOf("artist-a"))
    repository.recordDisplayedRecommendations(listOf("artist-b"))
    repository.recordDisplayedRecommendations(listOf("artist-c"))
    repository.recordDisplayedRecommendations(listOf("artist-d"))

    assertEquals(setOf("artist-b", "artist-c", "artist-d"), repository.loadExcludedUsernames())
  }

  @Test
  fun ignoresEmptyBatchWrites() = runTest {
    val repository = buildRepository()

    repository.recordDisplayedRecommendations(emptyList())
    repository.recordDisplayedRecommendations(listOf("artist-a"))
    repository.recordDisplayedRecommendations(listOf(" ", ""))

    assertEquals(setOf("artist-a"), repository.loadExcludedUsernames())
  }

  @Test
  fun normalizesAndDeduplicatesUsernamesAcrossDeviceSharedStorage() = runTest {
    val repository = buildRepository()

    repository.recordDisplayedRecommendations(listOf("Artist-A", "artist-a", "artist-b"))
    repository.recordDisplayedRecommendations(listOf("artist-c"))

    assertEquals(setOf("artist-a", "artist-b", "artist-c"), repository.loadExcludedUsernames())
  }

  private fun buildRepository(): WatchRecommendationCooldownRepository {
    val randomSuffix = Random.nextLong().toString().replace('-', '0')
    val tempPath =
        "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-watch-recommendation-$randomSuffix.preferences_pb"
            .toPath()
    val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { tempPath })
    return PersistedWatchRecommendationCooldownRepository(KeyValueStorage(dataStore))
  }
}
