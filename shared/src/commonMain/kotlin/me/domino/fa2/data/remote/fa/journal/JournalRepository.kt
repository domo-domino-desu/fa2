package me.domino.fa2.data.remote.fa.journal

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.core.PageType
import me.domino.fa2.data.remote.fa.core.cacheJson
import me.domino.fa2.data.remote.fa.core.fetchParsed
import me.domino.fa2.utils.FaUrls
import me.domino.fa2.utils.parseJournalId

class JournalRepository(
    private val html: FaHtmlDataSource,
    private val parser: JournalParser,
    private val queries: PageQueryCache,
) {
  fun journal(journalId: Int): Flow<QueryState<JournalDetail>> =
      queries.queryFlow(
          key(journalId),
          PageType.Journal,
          { cacheJson.encodeToString(it) },
          ::decode,
      ) {
        fetch(FaUrls.journal(journalId))
      }

  suspend fun loadJournalDetail(journalId: Int): ResourceResult<JournalDetail> =
      queries.queryOnce(
          key(journalId),
          PageType.Journal,
          { cacheJson.encodeToString(it) },
          ::decode,
      ) {
        fetch(FaUrls.journal(journalId))
      }

  suspend fun refreshJournal(journalId: Int): ResourceResult<JournalDetail> {
    queries.invalidate(key(journalId))
    return loadJournalDetail(journalId)
  }

  suspend fun loadJournalDetailByUrl(url: String): ResourceResult<JournalDetail> =
      parseJournalId(url)?.let { loadJournalDetail(it) } ?: fetch(url.trim())

  private suspend fun fetch(url: String) =
      html.fetchParsed(url) { body, finalUrl -> parser.parse(body, finalUrl) }

  private fun key(id: Int) = "journal:id=$id"

  private fun decode(json: String) =
      runCatching { cacheJson.decodeFromString<JournalDetail>(json) }.getOrNull()
}
