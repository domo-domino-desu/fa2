package me.domino.fa2.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.KeyValueStorage
import okio.FileSystem
import okio.Path.Companion.toPath

class WatchRecommendationBlocklistRepositoryTest {
  @Test
  fun returnsEmptyWhenStorageIsBlank() = runTest {
    val repository = buildRepository()

    assertEquals(emptySet(), repository.loadBlockedUsernameSet())
    assertEquals(emptyList(), repository.listBlockedUsernames())
  }

  @Test
  fun addsAndListsBlockedUsersInOrder() = runTest {
    val repository = buildRepository()

    repository.addBlockedUsername("artist-a")
    repository.addBlockedUsername("artist-b")
    repository.addBlockedUsername("artist-c")

    assertEquals(listOf("artist-a", "artist-b", "artist-c"), repository.listBlockedUsernames())
    assertEquals(setOf("artist-a", "artist-b", "artist-c"), repository.loadBlockedUsernameSet())
  }

  @Test
  fun ignoresEmptyWrites() = runTest {
    val repository = buildRepository()

    repository.addBlockedUsername(" ")
    repository.addBlockedUsername("artist-a")
    repository.addBlockedUsername("")

    assertEquals(setOf("artist-a"), repository.loadBlockedUsernameSet())
  }

  @Test
  fun normalizesAndDeduplicatesUsernamesAcrossDeviceSharedStorage() = runTest {
    val repository = buildRepository()

    repository.addBlockedUsername("Artist-A")
    repository.addBlockedUsername("artist-a")
    repository.addBlockedUsername("artist-b")
    repository.addBlockedUsername("artist-c")

    assertEquals(
        listOf("artist-a", "artist-b", "artist-c"),
        repository.listBlockedUsernames(),
    )
  }

  @Test
  fun removesBlockedUsers() = runTest {
    val repository = buildRepository()

    repository.addBlockedUsername("artist-a")
    repository.addBlockedUsername("artist-b")
    repository.removeBlockedUsername("Artist-A")

    assertEquals(listOf("artist-b"), repository.listBlockedUsernames())
  }

  private fun buildRepository(): WatchRecommendationBlocklistRepository {
    val randomSuffix = Random.nextLong().toString().replace('-', '0')
    val tempPath =
        "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-watch-recommendation-blocklist-$randomSuffix.preferences_pb"
            .toPath()
    val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { tempPath })
    return PersistedWatchRecommendationBlocklistRepository(KeyValueStorage(dataStore))
  }
}
