package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.util.ensureUserPageAccessible
import me.domino.fa2.util.toAbsoluteUrl

/** watchlist 列表解析器。 */
class WatchlistParser {
  /** 解析 watchlist 分页。 */
  fun parse(html: String, baseUrl: String): WatchlistPage {
    val document = Ksoup.parse(html, baseUrl)
    ensureUserPageAccessible(document)

    val users = parseUsers(document, baseUrl)
    val nextPageUrl = parseNextPageUrl(document, baseUrl)
    return WatchlistPage(users = users, nextPageUrl = nextPageUrl)
  }

  private fun parseUsers(
      document: com.fleeksoft.ksoup.nodes.Document,
      baseUrl: String,
  ): List<WatchlistUser> {
    val unique = LinkedHashMap<String, WatchlistUser>()
    document.select("div.watch-list-items a[href*='/user/']").forEach { anchor ->
      val parsed = parseWatchlistUser(anchor, baseUrl) ?: return@forEach
      unique.putIfAbsent(parsed.username.lowercase(), parsed)
    }
    return unique.values.toList()
  }

  private fun parseWatchlistUser(anchor: Element, baseUrl: String): WatchlistUser? {
    val rawHref = anchor.attr("href").trim().takeIf { href -> href.isNotBlank() } ?: return null
    val profileUrl = toAbsoluteUrl(baseUrl, rawHref)
    val username =
        rawHref.substringAfter("/user/", "").substringBefore('/').trim().ifBlank { null }
            ?: return null
    val displayName =
        anchor
            .selectFirst(".c-usernameBlockSimple__displayName")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { anchor.text().trim().removePrefix("~").ifBlank { username } }
    return WatchlistUser(
        username = username,
        displayName = displayName,
        profileUrl = profileUrl,
    )
  }

  private fun parseNextPageUrl(
      document: com.fleeksoft.ksoup.nodes.Document,
      baseUrl: String,
  ): String? {
    val nextForm =
        document.select("div.watchlist-navigation form").firstOrNull(::isNextFormEnabled)
            ?: return null
    val action =
        nextForm.attr("action").trim().takeIf { value -> value.isNotBlank() } ?: return null
    val absoluteAction = toAbsoluteUrl(baseUrl, action)
    val nextPage =
        nextForm.selectFirst("input[name=page]")?.attr("value")?.trim()?.takeIf { value ->
          value.isNotBlank()
        } ?: return absoluteAction
    return appendQueryParam(absoluteAction, "page", nextPage)
  }

  private fun isNextFormEnabled(form: Element): Boolean {
    val button = form.selectFirst("button") ?: return false
    val label = button.text().trim().lowercase()
    if (!label.startsWith("next")) return false
    return !button.hasAttr("disabled")
  }

  private fun appendQueryParam(url: String, key: String, value: String): String {
    if (url.contains("?")) return "$url&$key=$value"
    return "$url?$key=$value"
  }
}
