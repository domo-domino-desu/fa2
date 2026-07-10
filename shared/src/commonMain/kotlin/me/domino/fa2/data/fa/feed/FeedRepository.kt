package me.domino.fa2.data.fa.feed

import kotlinx.coroutines.flow.Flow
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.utils.FaUrls
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** Feed 仓储。 */
class FeedRepository(private val feedStore: FeedPageCache) {
  private val log = FaLog.withTag("FeedRepository")

  /** 读取首页 feed 数据流。 */
  fun streamFirstPage(): Flow<ResourceResult<FeedPage>> = feedStore.stream(fromSid = null)

  /** 读取指定分页游标的 feed 数据流。 */
  fun streamPage(fromSid: Int?): Flow<ResourceResult<FeedPage>> =
      feedStore.stream(fromSid = fromSid)

  /** 单次读取首页。 */
  suspend fun loadFirstPage(): ResourceResult<FeedPage> {
    val requestUrl = FaUrls.submissions(fromSid = null)
    log.d { "加载Feed首页 -> url=${summarizeUrl(requestUrl)}" }
    val state = feedStore.loadPageOnce(fromSid = null)
    log.d { "加载Feed首页 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 强制刷新首页。 */
  suspend fun refreshFirstPage(): ResourceResult<FeedPage> {
    val requestUrl = FaUrls.submissions(fromSid = null)
    log.i { "刷新Feed首页 -> url=${summarizeUrl(requestUrl)}" }
    val state = feedStore.refreshPage(fromSid = null)
    log.i { "刷新Feed首页 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 单次读取分页。 */
  suspend fun loadPage(fromSid: Int?): ResourceResult<FeedPage> {
    val requestUrl = FaUrls.submissions(fromSid)
    log.d { "加载Feed分页 -> fromSid=${fromSid ?: "first"},url=${summarizeUrl(requestUrl)}" }
    val state = feedStore.loadPageOnce(fromSid = fromSid)
    log.d { "加载Feed分页 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 按下一页 URL 读取分页。 */
  suspend fun loadPageByNextUrl(nextPageUrl: String): ResourceResult<FeedPage> {
    log.d { "加载Feed下一页 -> url=${summarizeUrl(nextPageUrl)}" }
    val state = feedStore.loadPageByNextUrl(nextPageUrl)
    log.d { "加载Feed下一页 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 按完整 URL 读取分页。 */
  suspend fun loadPageByUrl(url: String): ResourceResult<FeedPage> {
    log.d { "加载Feed分页(url) -> url=${summarizeUrl(url)}" }
    val state = feedStore.loadPageByUrl(url)
    log.d { "加载Feed分页(url) -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 预取指定分页。 */
  suspend fun prefetchPage(fromSid: Int?) {
    val requestUrl = FaUrls.submissions(fromSid)
    log.d { "预取Feed分页 -> fromSid=${fromSid ?: "first"},url=${summarizeUrl(requestUrl)}" }
    feedStore.prefetchPage(fromSid = fromSid)
  }
}
