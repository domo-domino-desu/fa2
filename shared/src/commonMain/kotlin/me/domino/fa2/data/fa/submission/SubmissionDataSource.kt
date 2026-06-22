package me.domino.fa2.data.fa.submission

import me.domino.fa2.data.fa.core.toPageState
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission

/** Submission 远端数据源。 */
class SubmissionDataSource(
    private val endpoint: SubmissionEndpoint,
    private val parser: SubmissionParser,
) {
  /** 按 ID 拉取投稿详情。 */
  suspend fun fetchBySid(sid: Int): PageState<Submission> =
      endpoint.fetchBySid(sid).toPageState { success ->
        parser.parse(html = success.body, url = success.url)
      }

  /** 按 URL 拉取投稿详情。 */
  suspend fun fetchByUrl(url: String): PageState<Submission> =
      endpoint.fetchByUrl(url).toPageState { success ->
        parser.parse(html = success.body, url = success.url)
      }
}
