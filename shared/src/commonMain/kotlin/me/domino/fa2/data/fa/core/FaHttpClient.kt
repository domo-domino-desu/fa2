package me.domino.fa2.data.fa.core

/** Boundary used by FA resource endpoints to retrieve and classify HTML pages. */
interface FaHtmlDataSource {
  suspend fun get(url: String): HtmlResponseResult
}
