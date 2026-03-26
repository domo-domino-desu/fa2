package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.model.PageComment
import me.domino.fa2.data.model.User
import me.domino.fa2.data.model.UserContact
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.ensureUserPageAccessible
import me.domino.fa2.util.toAbsoluteUrl

/** User 主页头信息解析器。 */
class UserParser {
  private val domainLikeUrlRegex =
      Regex(pattern = """^[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+(?::[0-9]+)?(?:/.*)?$""")
  private val backgroundUrlRegex =
      Regex(
          pattern = """background(?:-image)?\s*:\s*url\((['"]?)(.*?)\1\)""",
          options = setOf(RegexOption.IGNORE_CASE),
      )
  private val watchlistCountRegex =
      Regex(
          pattern = """\((?:Watched by|Watching)\s*([0-9,]+)\)""",
          options = setOf(RegexOption.IGNORE_CASE),
      )

  /** 解析用户主页头部信息。 */
  fun parse(html: String, url: String): User {
    val document = Ksoup.parse(html, url)
    ensureUserPageAccessible(document)

    val username = parseUsername(document)
    val displayName =
        document.selectFirst(".c-usernameBlock__displayName")?.text()?.trim()?.takeIf {
          it.isNotBlank()
        } ?: username
    val avatarUrl =
        document
            .selectFirst("userpage-nav-avatar img")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> toAbsoluteUrl(url, raw) } ?: ""

    val userTitle = parseUserTitle(document)
    val registeredAt =
        document.selectFirst(".user-title span.popup_date")?.attr("title")?.trim()?.takeIf {
          it.isNotBlank()
        }
            ?: document
                .selectFirst(".user-title span.popup_date")
                ?.text()
                ?.trim()
                .orEmpty()
                .ifBlank { "未知" }

    val watchState = parseWatchState(document, url)
    val watchlistInfo = parseWatchlistInfo(document, url)
    val profileBannerUrl = parseProfileBannerUrl(document = document, baseUrl = url)
    val shouts = parseShouts(document, pageUrl = url)
    val contacts = parseContacts(document, baseUrl = url)
    val profileNode =
        document.selectFirst("section.userpage-layout-profile .section-body.userpage-profile")
    val profileHtml = profileNode?.html()?.trim().orEmpty()

    return User(
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl,
        profileBannerUrl = profileBannerUrl,
        userTitle = userTitle,
        registeredAt = registeredAt,
        isWatching = watchState.isWatching,
        watchActionUrl = watchState.actionUrl,
        watchedByCount = watchlistInfo.watchedByCount,
        watchingCount = watchlistInfo.watchingCount,
        watchedByListUrl = watchlistInfo.watchedByListUrl,
        watchingListUrl = watchlistInfo.watchingListUrl,
        shoutCount = shouts.size,
        shouts = shouts,
        contacts = contacts,
        profileHtml = profileHtml,
    )
  }

  private fun parseUsername(document: com.fleeksoft.ksoup.nodes.Document): String {
    val fromHref =
        document
            .selectFirst("userpage-nav-avatar a[href*='/user/']")
            ?.attr("href")
            ?.substringAfter("/user/")
            ?.substringBefore('/')
            ?.trim()
            .orEmpty()
    if (fromHref.isNotBlank()) return fromHref

    val fromLabel =
        document
            .selectFirst(".c-usernameBlock__userName")
            ?.text()
            ?.trim()
            .orEmpty()
            .removePrefix("~")
            .removePrefix("@")
            .trim()
    return fromLabel.ifBlank { "unknown" }
  }

  private fun parseUserTitle(document: com.fleeksoft.ksoup.nodes.Document): String {
    val titleNode = document.selectFirst(".user-title") ?: return ""
    val ownText = titleNode.ownText().trim()
    if (ownText.isNotBlank()) {
      return ownText.removeSuffix("|").trim()
    }

    return titleNode.text().substringBefore("Registered:").removeSuffix("|").trim()
  }

  private fun parseWatchState(
      document: com.fleeksoft.ksoup.nodes.Document,
      baseUrl: String,
  ): WatchState {
    val actionNode =
        document.selectFirst(
            "userpage-nav-interface-buttons a[href*='/watch/'], " +
                "userpage-nav-interface-buttons a[href*='/unwatch/']"
        ) ?: return WatchState(isWatching = false, actionUrl = "")

    val href = actionNode.attr("href").trim()
    if (href.isBlank()) {
      return WatchState(isWatching = false, actionUrl = "")
    }
    val absoluteUrl = toAbsoluteUrl(baseUrl, href)
    val label = actionNode.text().trim().replace(" ", "").lowercase()
    val isWatching =
        absoluteUrl.contains("/unwatch/") || label.startsWith("-watch") || label.contains("unwatch")
    return WatchState(isWatching = isWatching, actionUrl = absoluteUrl)
  }

  private fun parseProfileBannerUrl(
      document: com.fleeksoft.ksoup.nodes.Document,
      baseUrl: String,
  ): String {
    val fromDesktopBannerSource =
        document
            .selectFirst("site-banner source[media*='min-width'][srcset]")
            ?.attr("srcset")
            ?.let { raw -> extractSrcsetFirstUrl(raw) }
            ?.trim()
            .orEmpty()
    if (fromDesktopBannerSource.isNotBlank()) {
      return toAbsoluteUrl(baseUrl, fromDesktopBannerSource)
    }

    val fromBannerImage =
        document
            .selectFirst(
                "site-banner img[alt*='Profile Banner image'], " +
                    "userpage-nav-header img[alt*='Profile Banner image']"
            )
            ?.attr("src")
            ?.trim()
            .orEmpty()
    if (fromBannerImage.isNotBlank()) {
      return toAbsoluteUrl(baseUrl, fromBannerImage)
    }

    val fromAnyBannerSource =
        document
            .selectFirst("site-banner source[srcset]")
            ?.attr("srcset")
            ?.let { raw -> extractSrcsetFirstUrl(raw) }
            ?.trim()
            .orEmpty()
    if (fromAnyBannerSource.isNotBlank()) {
      return toAbsoluteUrl(baseUrl, fromAnyBannerSource)
    }

    val inlineStyle = document.selectFirst("userpage-nav-header")?.attr("style")?.trim().orEmpty()

    val fromInlineStyle = extractBackgroundUrl(inlineStyle)
    if (!fromInlineStyle.isNullOrBlank()) {
      return toAbsoluteUrl(baseUrl, fromInlineStyle)
    }

    val fromEmbeddedStyle =
        document
            .select("style")
            .asSequence()
            .map { node -> node.html() }
            .filter { css ->
              css.contains("userpage-nav-header") && css.contains("background", ignoreCase = true)
            }
            .mapNotNull { css -> extractBackgroundUrl(css) }
            .firstOrNull()
    if (!fromEmbeddedStyle.isNullOrBlank()) {
      return toAbsoluteUrl(baseUrl, fromEmbeddedStyle)
    }

    return ""
  }

  private fun parseWatchlistInfo(
      document: com.fleeksoft.ksoup.nodes.Document,
      baseUrl: String,
  ): WatchlistInfo {
    val watchedByNode = document.selectFirst("a[href*='/watchlist/to/']")
    val watchingNode = document.selectFirst("a[href*='/watchlist/by/']")
    val watchedByUrl =
        watchedByNode
            ?.attr("href")
            .orEmpty()
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { href -> toAbsoluteUrl(baseUrl, href) }
            .orEmpty()
    val watchingUrl =
        watchingNode
            ?.attr("href")
            .orEmpty()
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { href -> toAbsoluteUrl(baseUrl, href) }
            .orEmpty()

    val watchedByCount = watchedByNode?.text().orEmpty().let(::parseWatchlistCount)
    val watchingCount = watchingNode?.text().orEmpty().let(::parseWatchlistCount)

    return WatchlistInfo(
        watchedByCount = watchedByCount,
        watchingCount = watchingCount,
        watchedByListUrl = watchedByUrl,
        watchingListUrl = watchingUrl,
    )
  }

  private fun parseContacts(
      document: com.fleeksoft.ksoup.nodes.Document,
      baseUrl: String,
  ): List<UserContact> =
      document.select("#userpage-contact .user-contact-item").mapNotNull { item ->
        val infoNode = item.selectFirst(".user-contact-user-info") ?: return@mapNotNull null
        val label =
            infoNode.selectFirst("strong.highlight")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
        val linkNode = infoNode.selectFirst("a[href]")
        val linkText = linkNode?.text()?.trim().orEmpty()
        val href = linkNode?.attr("href")?.trim().orEmpty()
        val resolvedUrl =
            normalizeContactUrl(href = href, displayText = linkText, baseUrl = baseUrl)
        val resolvedValue =
            when {
              linkText.isNotBlank() -> linkText
              else -> infoNode.text().removePrefix(label).trim()
            }
        resolvedValue
            .takeIf { it.isNotBlank() || resolvedUrl.isNotBlank() }
            ?.let { value ->
              UserContact(
                  label = label,
                  value = value.ifBlank { resolvedUrl },
                  url = resolvedUrl,
              )
            }
      }

  private fun parseShouts(
      document: com.fleeksoft.ksoup.nodes.Document,
      pageUrl: String,
  ): List<PageComment> =
      document.select(".userpage-shouts-container div.comment_container").mapNotNull { node ->
        parseShoutNode(shoutNode = node, pageUrl = pageUrl)
      }

  private fun parseShoutNode(shoutNode: Element, pageUrl: String): PageComment? {
    val shoutId =
        shoutNode
            .selectFirst("a.comment_anchor")
            ?.id()
            ?.substringAfter("shout-")
            ?.trim()
            ?.toLongOrNull() ?: return null

    val profileLink = shoutNode.selectFirst("comment-username a[href*='/user/']")
    val authorFromHref =
        profileLink?.attr("href")?.substringAfter("/user/")?.substringBefore('/')?.trim().orEmpty()
    val authorFromLabel =
        shoutNode
            .selectFirst(".c-usernameBlock__userName")
            ?.text()
            ?.trim()
            ?.replace("~", "")
            ?.replace("@", "")
            .orEmpty()
    val author = authorFromHref.ifBlank { authorFromLabel }.ifBlank { "unknown" }

    val displayName =
        shoutNode.selectFirst(".c-usernameBlock__displayName")?.text()?.trim().takeUnless {
          it.isNullOrBlank()
        } ?: author

    val avatarUrl =
        shoutNode
            .selectFirst("img.comment_useravatar")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> toAbsoluteUrl(pageUrl, raw) }
            .orEmpty()

    val timestampNode = shoutNode.selectFirst("comment-date .popup_date")
    val timestampRaw = timestampNode?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
    val timestampNatural = timestampNode?.text()?.trim().orEmpty().ifBlank { "未知时间" }

    val bodyHtml =
        shoutNode.selectFirst("comment-user-text .user-submitted-links")?.html()?.trim()?.takeIf {
          it.isNotBlank()
        } ?: shoutNode.selectFirst("comment-user-text")?.html()?.trim().orEmpty()

    return PageComment(
        id = shoutId,
        author = author,
        authorDisplayName = displayName,
        authorAvatarUrl = avatarUrl,
        timestampNatural = timestampNatural,
        timestampRaw = timestampRaw,
        bodyHtml = bodyHtml,
        depth = 0,
    )
  }

  private fun normalizeContactUrl(href: String, displayText: String, baseUrl: String): String {
    val normalizedHref = href.trim()
    if (
        normalizedHref.isBlank() ||
            normalizedHref.startsWith('#') ||
            normalizedHref.startsWith("javascript:", ignoreCase = true)
    ) {
      return normalizeDisplayUrl(displayText, baseUrl)
    }
    if (normalizedHref.startsWith("mailto:", ignoreCase = true)) {
      return normalizedHref
    }

    val displayUrl = normalizeDisplayUrl(displayText, baseUrl)
    if (displayUrl.isNotBlank()) {
      return displayUrl
    }

    return toAbsoluteUrl(baseUrl, normalizedHref)
  }

  private fun normalizeDisplayUrl(displayText: String, baseUrl: String): String {
    val normalizedText = displayText.trim()
    if (normalizedText.isBlank()) return ""
    if (normalizedText.startsWith("mailto:", ignoreCase = true)) return normalizedText
    if (
        normalizedText.startsWith("http://", ignoreCase = true) ||
            normalizedText.startsWith("https://", ignoreCase = true)
    ) {
      return normalizedText
    }
    if (normalizedText.startsWith("www.", ignoreCase = true)) {
      return "https://$normalizedText"
    }
    if (domainLikeUrlRegex.matches(normalizedText)) {
      return "https://$normalizedText"
    }
    if (normalizedText.startsWith('/')) {
      return toAbsoluteUrl(baseUrl, normalizedText)
    }
    return if (normalizedText.contains('/') && !normalizedText.contains(' ')) {
      toAbsoluteUrl(FaUrls.home.ifBlank { baseUrl }, normalizedText)
    } else {
      ""
    }
  }

  private fun parseWatchlistCount(rawLabel: String): Int? {
    val text = rawLabel.trim()
    if (text.isBlank()) return null
    val fromPattern =
        watchlistCountRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.trim()
            ?.toIntOrNull()
    if (fromPattern != null) return fromPattern
    return text.filter { ch -> ch.isDigit() }.toIntOrNull()
  }

  private fun extractBackgroundUrl(cssLike: String): String? =
      backgroundUrlRegex.find(cssLike)?.groupValues?.getOrNull(2)?.trim()?.takeIf {
        it.isNotBlank()
      }

  private fun extractSrcsetFirstUrl(rawSrcset: String): String =
      rawSrcset.substringBefore(',').substringBefore(' ').trim()

  private data class WatchState(val isWatching: Boolean, val actionUrl: String)

  private data class WatchlistInfo(
      val watchedByCount: Int?,
      val watchingCount: Int?,
      val watchedByListUrl: String,
      val watchingListUrl: String,
  )
}
