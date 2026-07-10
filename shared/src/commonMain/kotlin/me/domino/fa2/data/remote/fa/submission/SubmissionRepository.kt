package me.domino.fa2.data.remote.fa.submission

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.data.remote.fa.listing.FavoritesCacheInvalidator
import me.domino.fa2.data.remote.fa.social.SocialActionResult
import me.domino.fa2.data.remote.fa.social.SocialActionSource
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** Submission 仓储。 */
class SubmissionRepository(
    private val submissionStore: SubmissionPageCache,
    private val socialActionEndpoint: SocialActionSource,
    private val favoritesCacheInvalidator: FavoritesCacheInvalidator,
) : SubmissionSeriesSubmissionSource {
  private val log = FaLog.withTag("SubmissionRepository")

  /** 按 sid 加载 submission 详情。 */
  override suspend fun loadSubmissionDetailBySid(sid: Int): ResourceResult<Submission> {
    log.d { "加载投稿详情 -> sid=$sid" }
    val state = submissionStore.loadBySid(sid)
    log.d { "加载投稿详情 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 按 URL 加载 submission 详情。 */
  override suspend fun loadSubmissionDetailByUrl(url: String): ResourceResult<Submission> {
    log.d { "加载投稿详情 -> url=${summarizeUrl(url)}" }
    val state = submissionStore.loadByUrl(url)
    log.d { "加载投稿详情 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 预取 submission 详情。 */
  suspend fun prefetchSubmissionDetailBySid(sid: Int) {
    log.d { "预取投稿详情 -> sid=$sid" }
    submissionStore.prefetchBySid(sid)
  }

  /** 收藏/取消收藏投稿。 */
  suspend fun toggleFavorite(sid: Int, actionUrl: String): ResourceResult<Unit> {
    val normalizedUrl = actionUrl.trim()
    if (normalizedUrl.isBlank()) {
      log.w { "收藏操作 -> 缺少actionUrl" }
      return ResourceResult.Error(IllegalArgumentException("Missing favorite action url"))
    }
    log.i { "收藏操作 -> sid=$sid,url=${summarizeUrl(normalizedUrl)}" }

    val state =
        when (val response = socialActionEndpoint.execute(normalizedUrl)) {
          is SocialActionResult.Completed -> {
            submissionStore.invalidateBySid(sid)
            favoritesCacheInvalidator.invalidateFavorites()
            ResourceResult.Success(Unit)
          }

          is SocialActionResult.Challenge ->
              ResourceResult.Error(IllegalStateException("Cloudflare challenge unresolved"))

          is SocialActionResult.Blocked -> ResourceResult.MatureBlocked(response.reason)
          is SocialActionResult.Failed ->
              ResourceResult.Error(IllegalStateException(response.message))
        }
    log.i { "收藏操作 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 屏蔽标签。 */
  suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): ResourceResult<Unit> {
    val normalizedTagName = tagName.trim()
    val normalizedNonce = nonce.trim()
    if (normalizedTagName.isBlank()) {
      log.w { "标签屏蔽 -> 缺少tagName" }
      return ResourceResult.Error(IllegalArgumentException("Missing tag name"))
    }
    if (normalizedNonce.isBlank()) {
      log.w { "标签屏蔽 -> 缺少nonce" }
      return ResourceResult.Error(IllegalArgumentException("Missing tag block nonce"))
    }
    log.i { "标签屏蔽 -> sid=$sid,tag=$normalizedTagName,toAdd=$toAdd" }

    val state =
        when (
            val response =
                socialActionEndpoint.updateTagBlocklist(
                    tagName = normalizedTagName,
                    nonce = normalizedNonce,
                    toAdd = toAdd,
                )
        ) {
          is SocialActionResult.Completed -> {
            submissionStore.invalidateBySid(sid)
            ResourceResult.Success(Unit)
          }

          is SocialActionResult.Challenge ->
              ResourceResult.Error(IllegalStateException("Cloudflare challenge unresolved"))

          is SocialActionResult.Blocked -> ResourceResult.MatureBlocked(response.reason)
          is SocialActionResult.Failed ->
              ResourceResult.Error(IllegalStateException(response.message))
        }
    log.i { "标签屏蔽 -> ${summarizeResourceResult(state)}" }
    return state
  }
}
