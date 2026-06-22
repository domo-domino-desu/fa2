package me.domino.fa2.data.fa.search

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.model.summarizePageState
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** Search 仓储。 */
class SearchRepository(private val store: SearchPageCache) {
  private val log = FaLog.withTag("SearchRepository")

  suspend fun loadPage(url: String): PageState<SubmissionListingPage> {
    val safeUrl = summarizeUrl(url)
    log.d { "加载Search -> url=$safeUrl" }
    val state = store.loadPageOnce(requestUrl = url)
    log.d { "加载Search -> ${summarizePageState(state)}" }
    return state
  }

  suspend fun refreshPage(url: String): PageState<SubmissionListingPage> {
    val safeUrl = summarizeUrl(url)
    log.i { "刷新Search -> url=$safeUrl" }
    val state = store.refreshPage(requestUrl = url)
    log.i { "刷新Search -> ${summarizePageState(state)}" }
    return state
  }
}
