package me.domino.fa2.data.remote.fa.journal

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.core.PageType
import me.domino.fa2.data.remote.fa.core.cacheJson
import me.domino.fa2.data.remote.fa.core.fetchParsed
import me.domino.fa2.utils.FaUrls

class JournalsRepository(
    private val html: FaHtmlDataSource,
    private val parser: JournalsParser,
    private val queries: PageQueryCache,
) {
  fun journals(username: String, nextPageUrl: String? = null): Flow<QueryState<JournalPage>> {
    val url = nextPageUrl?.trim()?.ifBlank { null } ?: FaUrls.journals(username)
    return queries.queryFlow(
        key(username, url),
        PageType.Journals,
        { cacheJson.encodeToString(it) },
        ::decode,
    ) {
      fetch(url)
    }
  }

  suspend fun loadJournalsPage(
      username: String,
      nextPageUrl: String? = null,
  ): ResourceResult<JournalPage> {
    val url = nextPageUrl?.trim()?.ifBlank { null } ?: FaUrls.journals(username)
    return queries.queryOnce(
        key(username, url),
        PageType.Journals,
        { cacheJson.encodeToString(it) },
        ::decode,
    ) {
      fetch(url)
    }
  }

  suspend fun refreshJournalsFirstPage(username: String): ResourceResult<JournalPage> {
    queries.invalidate(PageType.Journals)
    return loadJournalsPage(username)
  }

  private suspend fun fetch(url: String) =
      html.fetchParsed(url) { body, finalUrl -> parser.parse(body, finalUrl) }

  private fun key(username: String, url: String) =
      "journals:username=${username.trim().lowercase()}:cursor=$url"

  private fun decode(json: String) =
      runCatching { cacheJson.decodeFromString<JournalPage>(json) }.getOrNull()
}
