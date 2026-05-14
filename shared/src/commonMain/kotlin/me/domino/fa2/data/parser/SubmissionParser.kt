package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import me.domino.fa2.data.model.Submission

/** Submission 详情页解析器。 */
class SubmissionParser {
  private val pageParser = SubmissionPageParser()

  /**
   * 解析 submission 详情页。
   *
   * @param html 页面 HTML。
   * @param url 页面 URL。
   */
  fun parse(html: String, url: String): Submission {
    val document = Ksoup.parse(html, url)
    if (document.selectFirst("#submission_page .submission-page-content") == null) {
      throw IllegalStateException("Submission page layout missing")
    }
    return pageParser.parse(document, url)
  }
}
