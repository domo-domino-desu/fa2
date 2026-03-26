package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.JournalSummary
import me.domino.fa2.util.ensureUserPageAccessible
import me.domino.fa2.util.toAbsoluteUrl

/** Journals 列表解析器。 */
class JournalsParser {
  /** 解析 journals 列表页。 */
  fun parse(html: String, baseUrl: String): JournalPage {
    val document = Ksoup.parse(html, baseUrl)
    ensureUserPageAccessible(document)

    val sections = document.select("div.content section[id^='jid:']")
    val journals = sections.mapNotNull(::parseJournalSummary)

    val nextPageUrl =
        document
            .select("a, form")
            .firstOrNull(::isOlderButton)
            ?.let { node ->
              val raw = node.attr("href").ifBlank { node.attr("action") }
              raw.trim().takeIf { it.isNotBlank() }
            }
            ?.let { raw -> toAbsoluteUrl(baseUrl, raw) }

    return JournalPage(journals = journals, nextPageUrl = nextPageUrl)
  }

  private fun parseJournalSummary(section: Element): JournalSummary? {
    val sid = section.id().substringAfter("jid:", "").toIntOrNull() ?: return null
    val title =
        section.selectFirst("div.section-header h2")?.text()?.trim().orEmpty().ifBlank {
          "Untitled Journal #$sid"
        }
    val timestampNode = section.selectFirst("div.section-header span.popup_date")
    val timestampRaw = timestampNode?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
    val timestampNatural = timestampNode?.text()?.trim().orEmpty().ifBlank { "未知时间" }

    val commentCount =
        section
            .selectFirst("div.section-footer span.font-large")
            ?.text()
            ?.filter { ch -> ch.isDigit() }
            ?.toIntOrNull() ?: 0

    val journalUrl =
        section
            .selectFirst("div.section-footer a[href*='/journal/']")
            ?.attr("href")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { href -> toAbsoluteUrl("https://www.furaffinity.net/", href) }
            ?: "https://www.furaffinity.net/journal/$sid/"

    val bodyText =
        section
            .selectFirst("div.section-body div.journal-body")
            ?.html()
            ?.let { bodyHtml -> Ksoup.parseBodyFragment(bodyHtml).text().trim() }
            .orEmpty()
    val excerpt = buildExcerpt(bodyText)

    return JournalSummary(
        id = sid,
        title = title,
        journalUrl = journalUrl,
        timestampNatural = timestampNatural,
        timestampRaw = timestampRaw,
        commentCount = commentCount,
        excerpt = excerpt,
    )
  }

  private fun isOlderButton(node: Element): Boolean {
    val label = node.text().trim().lowercase()
    val rel = node.attr("rel").trim().lowercase()
    val aria = node.attr("aria-label").trim().lowercase()
    return rel == "next" || aria.startsWith("older") || label.startsWith("older")
  }

  private fun buildExcerpt(text: String): String {
    if (text.isBlank()) return ""
    val maxLength = 220
    if (text.length <= maxLength) return text
    return text.take(maxLength).trimEnd() + "..."
  }
}
