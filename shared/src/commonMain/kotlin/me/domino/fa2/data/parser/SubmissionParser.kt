package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import me.domino.fa2.data.model.Submission

/** Submission 详情页解析器。 */
class SubmissionParser {
  private val legacyParser = LegacySubmissionParser()
  private val modernParser = ModernSubmissionParser()

  /**
   * 解析 submission 详情页。
   *
   * @param html 页面 HTML。
   * @param url 页面 URL。
   */
  fun parse(html: String, url: String): Submission {
    val document = Ksoup.parse(html, url)
    return when {
      document.selectFirst("#submission_page .submission-page-content") != null ->
          modernParser.parse(document, url)
      document.selectFirst("#columnpage") != null -> legacyParser.parse(document, url)
      else -> throw IllegalStateException("Submission page layout missing")
    }
  }
}
