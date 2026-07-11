package me.domino.fa2.data.remote.fa.submission

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.core.PageType
import me.domino.fa2.data.remote.fa.core.cacheJson
import me.domino.fa2.data.remote.fa.core.fetchParsed
import me.domino.fa2.data.remote.fa.social.SocialActionResult
import me.domino.fa2.data.remote.fa.social.SocialActionSource
import me.domino.fa2.utils.FaUrls
import me.domino.fa2.utils.parseSubmissionSid

class SubmissionRepository(
    private val html: FaHtmlDataSource,
    private val parser: SubmissionParser,
    private val queries: PageQueryCache,
    private val actions: SocialActionSource,
) : SubmissionSeriesSubmissionSource {
  fun submission(sid: Int): Flow<QueryState<Submission>> =
      queries.queryFlow(key(sid), PageType.Submission, { cacheJson.encodeToString(it) }, ::decode) {
        fetch(FaUrls.submission(sid))
      }

  override suspend fun loadSubmissionDetailBySid(sid: Int): ResourceResult<Submission> =
      queries.queryOnce(key(sid), PageType.Submission, { cacheJson.encodeToString(it) }, ::decode) {
        fetch(FaUrls.submission(sid))
      }

  override suspend fun loadSubmissionDetailByUrl(url: String): ResourceResult<Submission> =
      parseSubmissionSid(url)?.let { loadSubmissionDetailBySid(it) } ?: fetch(url.trim())

  suspend fun prefetchSubmissionDetailBySid(sid: Int) {
    loadSubmissionDetailBySid(sid)
  }

  suspend fun toggleFavorite(sid: Int, actionUrl: String): ResourceResult<Unit> =
      execute(actionUrl) {
        queries.invalidate(key(sid))
        queries.invalidate(PageType.Favorites)
      }

  suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): ResourceResult<Unit> {
    if (tagName.isBlank() || nonce.isBlank()) {
      return ResourceResult.Error(IllegalArgumentException("Missing tag block arguments"))
    }
    return mapAction(actions.updateTagBlocklist(tagName.trim(), nonce.trim(), toAdd)) {
      queries.invalidate(key(sid))
    }
  }

  private suspend fun execute(url: String, completed: suspend () -> Unit): ResourceResult<Unit> {
    if (url.isBlank()) return ResourceResult.Error(IllegalArgumentException("Missing action URL"))
    return mapAction(actions.execute(url.trim()), completed)
  }

  private suspend fun mapAction(
      result: SocialActionResult,
      completed: suspend () -> Unit,
  ): ResourceResult<Unit> =
      when (result) {
        is SocialActionResult.Completed -> {
          completed()
          ResourceResult.Success(Unit)
        }
        is SocialActionResult.Challenge -> ResourceResult.CfChallenge
        is SocialActionResult.Blocked -> ResourceResult.MatureBlocked(result.reason)
        is SocialActionResult.Failed -> ResourceResult.Error(IllegalStateException(result.message))
      }

  private suspend fun fetch(url: String) =
      html.fetchParsed(url) { body, finalUrl -> parser.parse(body, finalUrl) }

  private fun key(sid: Int) = "submission:sid=$sid"

  private fun decode(json: String) =
      runCatching { cacheJson.decodeFromString<Submission>(json) }.getOrNull()
}
