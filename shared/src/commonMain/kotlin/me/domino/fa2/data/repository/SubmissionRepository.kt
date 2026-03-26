package me.domino.fa2.data.repository

import me.domino.fa2.application.attachmenttext.AttachmentTextService
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.network.endpoint.SocialActionEndpoint
import me.domino.fa2.data.network.endpoint.SocialActionResult
import me.domino.fa2.data.store.GalleryStore
import me.domino.fa2.data.store.SubmissionStore
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState
import me.domino.fa2.util.logging.summarizeUrl

/** Submission 仓储。 */
interface SubmissionDetailRepository {
  suspend fun loadSubmissionDetailBySid(sid: Int): PageState<Submission>

  suspend fun loadSubmissionDetailByUrl(url: String): PageState<Submission>
}

/** Submission 仓储。 */
class SubmissionRepository(
    private val submissionStore: SubmissionStore,
    private val socialActionEndpoint: SocialActionEndpoint,
    private val galleryStore: GalleryStore,
    private val attachmentTextService: AttachmentTextService,
) : SubmissionDetailRepository {
  private val log = FaLog.withTag("SubmissionRepository")

  /** 按 sid 加载 submission 详情。 */
  override suspend fun loadSubmissionDetailBySid(sid: Int): PageState<Submission> {
    log.d { "加载投稿详情 -> sid=$sid" }
    val state = submissionStore.loadBySid(sid)
    log.d { "加载投稿详情 -> ${summarizePageState(state)}" }
    return state
  }

  /** 按 URL 加载 submission 详情。 */
  override suspend fun loadSubmissionDetailByUrl(url: String): PageState<Submission> {
    log.d { "加载投稿详情 -> url=${summarizeUrl(url)}" }
    val state = submissionStore.loadByUrl(url)
    log.d { "加载投稿详情 -> ${summarizePageState(state)}" }
    return state
  }

  /** 预取 submission 详情。 */
  suspend fun prefetchSubmissionDetailBySid(sid: Int) {
    log.d { "预取投稿详情 -> sid=$sid" }
    submissionStore.prefetchBySid(sid)
  }

  /** 下载并解析附件文本。 */
  suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit = {},
  ): PageState<AttachmentTextDocument> =
      runCatching {
            attachmentTextService.load(
                downloadUrl = downloadUrl,
                downloadFileName = downloadFileName,
                onProgress = onProgress,
            )
          }
          .getOrElse { error -> PageState.Error(error) }

  /** 收藏/取消收藏投稿。 */
  suspend fun toggleFavorite(sid: Int, actionUrl: String): PageState<Unit> {
    val normalizedUrl = actionUrl.trim()
    if (normalizedUrl.isBlank()) {
      log.w { "收藏操作 -> 缺少actionUrl" }
      return PageState.Error(IllegalArgumentException("Missing favorite action url"))
    }
    log.i { "收藏操作 -> sid=$sid,url=${summarizeUrl(normalizedUrl)}" }

    val state =
        when (val response = socialActionEndpoint.execute(normalizedUrl)) {
          is SocialActionResult.Completed -> {
            submissionStore.invalidateBySid(sid)
            galleryStore.invalidateSection(GalleryStore.Section.Favorites)
            PageState.Success(Unit)
          }

          is SocialActionResult.Challenge ->
              PageState.Error(IllegalStateException("Cloudflare challenge unresolved"))

          is SocialActionResult.Blocked -> PageState.MatureBlocked(response.reason)
          is SocialActionResult.Failed -> PageState.Error(IllegalStateException(response.message))
        }
    log.i { "收藏操作 -> ${summarizePageState(state)}" }
    return state
  }

  /** 屏蔽标签。 */
  suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): PageState<Unit> {
    val normalizedTagName = tagName.trim()
    val normalizedNonce = nonce.trim()
    if (normalizedTagName.isBlank()) {
      log.w { "标签屏蔽 -> 缺少tagName" }
      return PageState.Error(IllegalArgumentException("Missing tag name"))
    }
    if (normalizedNonce.isBlank()) {
      log.w { "标签屏蔽 -> 缺少nonce" }
      return PageState.Error(IllegalArgumentException("Missing tag block nonce"))
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
            PageState.Success(Unit)
          }

          is SocialActionResult.Challenge ->
              PageState.Error(IllegalStateException("Cloudflare challenge unresolved"))

          is SocialActionResult.Blocked -> PageState.MatureBlocked(response.reason)
          is SocialActionResult.Failed -> PageState.Error(IllegalStateException(response.message))
        }
    log.i { "标签屏蔽 -> ${summarizePageState(state)}" }
    return state
  }
}
