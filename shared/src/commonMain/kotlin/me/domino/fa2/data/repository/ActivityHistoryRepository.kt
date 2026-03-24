package me.domino.fa2.data.repository

import kotlin.time.Clock
import me.domino.fa2.data.local.dao.HistoryDao
import me.domino.fa2.data.local.entity.SearchHistoryEntity
import me.domino.fa2.data.local.entity.SubmissionHistoryEntity
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.util.logging.FaLog

/** 用户浏览历史仓储（投稿 + 搜索）。 */
class ActivityHistoryRepository(private val historyDao: HistoryDao) {
  private val log = FaLog.withTag("ActivityHistoryRepository")

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
              isBlockedByTag = entry.isBlockedByTag,
          )
        }
    log.d { "读取投稿历史 -> 成功(count=${history.size})" }
    return history
  }

  suspend fun recordSearchQuery(query: String) {
    val normalized = query.trim()
    if (normalized.isBlank()) return
    log.d { "记录搜索历史 -> query=${normalized.take(40)}" }
    val nowMs = Clock.System.now().toEpochMilliseconds()
    historyDao.upsertSearch(
        SearchHistoryEntity(
            queryKey = normalized.lowercase(),
            query = normalized,
            visitedAtMs = nowMs,
        )
    )
  }

  suspend fun loadSearchHistory(): List<String> {
    val history =
        historyDao
            .listSearchesByLatest()
            .take(maxSearchHistoryCount)
            .map { entry -> entry.query.trim() }
            .filter { value -> value.isNotBlank() }
    log.d { "读取搜索历史 -> 成功(count=${history.size})" }
    return history
  }

  companion object {
    private const val maxSubmissionHistoryCount: Int = 300
    private const val maxSearchHistoryCount: Int = 200
  }
}
