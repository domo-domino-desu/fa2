package me.domino.fa2.data.fa.browse

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.model.summarizePageState
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** Browse 仓储。 */
class BrowseRepository(private val store: BrowsePageCache) {
  private val log = FaLog.withTag("BrowseRepository")

  suspend fun loadPage(url: String): PageState<SubmissionListingPage> {
    val safeUrl = summarizeUrl(url)
    log.d { "加载Browse -> url=$safeUrl" }
    val state = store.loadPageOnce(requestUrl = url)
    log.d { "加载Browse -> ${summarizePageState(state)}" }
    return state
  }

  suspend fun refreshPage(url: String): PageState<SubmissionListingPage> {
    val safeUrl = summarizeUrl(url)
    log.i { "刷新Browse -> url=$safeUrl" }
    val state = store.refreshPage(requestUrl = url)
    log.i { "刷新Browse -> ${summarizePageState(state)}" }
    return state
  }
}
