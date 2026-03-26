package me.domino.fa2.util

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

private val tagTokenSplitRegex = Regex("""[\s,]+""")

/** 页面级 tag 屏蔽设置。 */
data class TagBlockSettings(
    val blockedTags: Set<String>,
    val hideTagless: Boolean,
)

/** 解析页面 body 上的屏蔽设置。 */
fun parseTagBlockSettings(document: Document): TagBlockSettings {
  val body = document.selectFirst("body")
  val blockedTags = parseTagTokens(body?.attr("data-tag-blocklist").orEmpty())
  val hideTagless =
      body?.attr("data-tag-blocklist-hide-tagless")?.trim()?.let { raw ->
        raw == "1" || raw.equals("true", ignoreCase = true)
      } ?: false
  return TagBlockSettings(blockedTags = blockedTags, hideTagless = hideTagless)
}

/** 解析图片上的 `data-tags`。 */
fun parseImageTags(image: Element): Set<String> = parseTagTokens(image.attr("data-tags"))

/** 判断投稿是否命中 tag 屏蔽。 */
fun isBlockedByTagSettings(
    imageTags: Set<String>,
    tagBlockSettings: TagBlockSettings,
): Boolean =
    imageTags.any { tag -> tag in tagBlockSettings.blockedTags } ||
        (tagBlockSettings.hideTagless && imageTags.isEmpty())

private fun parseTagTokens(raw: String): Set<String> =
    raw.split(tagTokenSplitRegex)
        .asSequence()
        .map { token -> token.trim().lowercase() }
        .filter { token -> token.isNotBlank() }
        .toSet()
