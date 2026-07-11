package me.domino.fa2.data.local.watchrecommendation

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.remote.fa.core.cacheJson
import me.domino.fa2.utils.logging.FaLog

class PersistedWatchRecommendationBlocklistRepository(
    private val keyValueStorage: KeyValueStorage
) : WatchRecommendationBlocklist {
  private val log = FaLog.withTag("WatchRecommendationBlocklist")

  override suspend fun loadBlockedUsernameSet(): Set<String> {
    val snapshot = loadSnapshot()
    log.d { "推荐屏蔽名单仓储 -> 读取集合(count=${snapshot.blockedUsernames.size})" }
    return snapshot.blockedUsernames.toSet()
  }

  override suspend fun listBlockedUsernames(): List<String> =
      loadSnapshot().blockedUsernames.also { usernames ->
        log.d { "推荐屏蔽名单仓储 -> 列表(count=${usernames.size})" }
      }

  override suspend fun addBlockedUsername(username: String) {
    val normalized = normalizeUsername(username)
    if (normalized == null) {
      log.w { "推荐屏蔽名单仓储 -> 添加跳过(空用户名)" }
      return
    }
    val current = loadSnapshot()
    if (normalized in current.blockedUsernames) {
      log.d { "推荐屏蔽名单仓储 -> 添加跳过(已存在,user=$normalized)" }
      return
    }
    val next =
        WatchRecommendationBlocklistSnapshot(
            blockedUsernames = current.blockedUsernames + normalized
        )
    keyValueStorage.save(KEY_WATCH_RECOMMENDATION_BLOCKLIST, cacheJson.encodeToString(next))
    log.i { "推荐屏蔽名单仓储 -> 已添加(user=$normalized,count=${next.blockedUsernames.size})" }
  }

  override suspend fun removeBlockedUsername(username: String) {
    val normalized = normalizeUsername(username)
    if (normalized == null) {
      log.w { "推荐屏蔽名单仓储 -> 移除跳过(空用户名)" }
      return
    }
    val current = loadSnapshot()
    if (normalized !in current.blockedUsernames) {
      log.d { "推荐屏蔽名单仓储 -> 移除跳过(不存在,user=$normalized)" }
      return
    }
    val next =
        WatchRecommendationBlocklistSnapshot(
            blockedUsernames = current.blockedUsernames.filterNot { it == normalized }
        )
    keyValueStorage.save(KEY_WATCH_RECOMMENDATION_BLOCKLIST, cacheJson.encodeToString(next))
    log.i { "推荐屏蔽名单仓储 -> 已移除(user=$normalized,count=${next.blockedUsernames.size})" }
  }

  private suspend fun loadSnapshot(): WatchRecommendationBlocklistSnapshot {
    val raw = keyValueStorage.load(KEY_WATCH_RECOMMENDATION_BLOCKLIST)?.trim().orEmpty()
    if (raw.isBlank()) {
      log.d { "推荐屏蔽名单仓储 -> 读取快照(空)" }
      return WatchRecommendationBlocklistSnapshot()
    }
    return runCatching { cacheJson.decodeFromString<WatchRecommendationBlocklistSnapshot>(raw) }
        .onFailure { error -> log.e(error) { "推荐屏蔽名单仓储 -> 反序列化失败,回退默认值" } }
        .getOrDefault(WatchRecommendationBlocklistSnapshot())
        .normalized()
        .also { snapshot ->
          log.d { "推荐屏蔽名单仓储 -> 读取快照成功(count=${snapshot.blockedUsernames.size})" }
        }
  }

  private fun normalizeUsername(username: String): String? =
      username.trim().lowercase().takeIf { it.isNotBlank() }

  companion object {
    internal const val KEY_WATCH_RECOMMENDATION_BLOCKLIST: String =
        "watch_recommendation.blocklist.v1"
  }
}

@Serializable
data class WatchRecommendationBlocklistSnapshot(val blockedUsernames: List<String> = emptyList()) {
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
