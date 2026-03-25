package me.domino.fa2.data.repository

import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.domino.fa2.data.local.dao.HistoryDao
import me.domino.fa2.data.local.entity.SearchHistoryEntity
import me.domino.fa2.data.local.entity.SubmissionHistoryEntity
import me.domino.fa2.data.model.SearchHistoryRecord
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.util.logging.FaLog

/** 用户浏览历史仓储（投稿 + 搜索）。 */
class ActivityHistoryRepository(private val historyDao: HistoryDao) {
  private val log = FaLog.withTag("ActivityHistoryRepository")
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun recordSubmissionVisit(item: SubmissionThumbnail) {
    log.d { "记录投稿历史 -> sid=${item.id}" }
    val nowMs = Clock.System.now().toEpochMilliseconds()
    historyDao.upsertSubmission(
        SubmissionHistoryEntity(
            sid = item.id,
            visitedAtMs = nowMs,
            submissionUrl = item.submissionUrl,
            title = item.title,
            author = item.author,
            thumbnailUrl = item.thumbnailUrl,
            thumbnailAspectRatio = item.thumbnailAspectRatio,
            authorAvatarUrl = item.authorAvatarUrl,
            categoryTag = item.categoryTag,
            isBlockedByTag = item.isBlockedByTag,
        )
    )
  }

  suspend fun loadSubmissionHistory(): List<SubmissionThumbnail> {
    val history =
        historyDao.listSubmissionsByLatest().take(maxSubmissionHistoryCount).map { entry ->
          SubmissionThumbnail(
              id = entry.sid,
              submissionUrl = entry.submissionUrl,
              title = entry.title,
              author = entry.author,
              thumbnailUrl = entry.thumbnailUrl,
              thumbnailAspectRatio = entry.thumbnailAspectRatio,
              authorAvatarUrl = entry.authorAvatarUrl,
              categoryTag = entry.categoryTag,
              isBlockedByTag = entry.isBlockedByTag,
          )
        }
    log.d { "读取投稿历史 -> 成功(count=${history.size})" }
    return history
  }

  suspend fun recordSearchQuery(
      query: String,
      filtersSummary: String = "",
      searchUrl: String? = null,
  ) {
    val normalized = query.trim()
    if (normalized.isBlank()) return
    val normalizedSummary = filtersSummary.trim()
    val normalizedSearchUrl = searchUrl?.trim().orEmpty()
    val shouldStorePayload = normalizedSummary.isNotBlank() || normalizedSearchUrl.isNotBlank()
    log.d { "记录搜索历史 -> query=${normalized.take(40)}" }
    val nowMs = Clock.System.now().toEpochMilliseconds()
    historyDao.upsertSearch(
        SearchHistoryEntity(
            queryKey =
                buildString {
                  append(normalized.lowercase())
                  if (normalizedSummary.isNotBlank()) {
                    append(searchHistoryKeySeparator)
                    append(normalizedSummary.lowercase())
                  }
                },
            query =
                if (!shouldStorePayload) {
                  normalized
                } else {
                  searchHistoryPayloadPrefix +
                      json.encodeToString(
                          StoredSearchHistoryPayload(
                              query = normalized,
                              filtersSummary = normalizedSummary,
                              searchUrl = normalizedSearchUrl,
                          )
                      )
                },
            visitedAtMs = nowMs,
        )
    )
  }

  suspend fun loadSearchHistory(): List<SearchHistoryRecord> {
    val history =
        historyDao.listSearchesByLatest().take(maxSearchHistoryCount).mapNotNull { entry ->
          parseStoredSearch(entry.query)
        }
    log.d { "读取搜索历史 -> 成功(count=${history.size})" }
    return history
  }

  private fun parseStoredSearch(raw: String): SearchHistoryRecord? {
    val normalized = raw.trim()
    if (normalized.isBlank()) return null
    if (!normalized.startsWith(searchHistoryPayloadPrefix)) {
      return SearchHistoryRecord(query = normalized)
    }

    val payloadRaw = normalized.removePrefix(searchHistoryPayloadPrefix).trim()
    if (payloadRaw.isBlank()) return null
    val payload =
        runCatching { json.decodeFromString<StoredSearchHistoryPayload>(payloadRaw) }.getOrNull()
            ?: return SearchHistoryRecord(query = normalized)
    val query = payload.query.trim()
    if (query.isBlank()) return null
    return SearchHistoryRecord(
        query = query,
        filtersSummary = payload.filtersSummary.trim(),
        searchUrl = payload.searchUrl.trim().takeIf { value -> value.isNotBlank() },
    )
  }

  @Serializable
  private data class StoredSearchHistoryPayload(
      val query: String,
      val filtersSummary: String = "",
      val searchUrl: String = "",
  )

  companion object {
    private const val maxSubmissionHistoryCount: Int = 300
    private const val maxSearchHistoryCount: Int = 200
    private const val searchHistoryPayloadPrefix: String = "__fa2_search_v2__:"
    private const val searchHistoryKeySeparator: String = "\u001f"
  }
}
