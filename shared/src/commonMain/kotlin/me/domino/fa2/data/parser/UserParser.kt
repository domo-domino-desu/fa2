package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import me.domino.fa2.data.model.User
import me.domino.fa2.util.ParserUtils

/** User 主页头信息解析器。 */
class UserParser {
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
    ParserUtils.ensureUserPageAccessible(document)

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
            ?.let { raw -> ParserUtils.toAbsoluteUrl(url, raw) } ?: ""

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
    val absoluteUrl = ParserUtils.toAbsoluteUrl(baseUrl, href)
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
      return ParserUtils.toAbsoluteUrl(baseUrl, fromDesktopBannerSource)
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
      return ParserUtils.toAbsoluteUrl(baseUrl, fromBannerImage)
    }

    val fromAnyBannerSource =
        document
            .selectFirst("site-banner source[srcset]")
            ?.attr("srcset")
            ?.let { raw -> extractSrcsetFirstUrl(raw) }
            ?.trim()
            .orEmpty()
    if (fromAnyBannerSource.isNotBlank()) {
      return ParserUtils.toAbsoluteUrl(baseUrl, fromAnyBannerSource)
    }

    val inlineStyle = document.selectFirst("userpage-nav-header")?.attr("style")?.trim().orEmpty()

    val fromInlineStyle = extractBackgroundUrl(inlineStyle)
    if (!fromInlineStyle.isNullOrBlank()) {
      return ParserUtils.toAbsoluteUrl(baseUrl, fromInlineStyle)
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
      return ParserUtils.toAbsoluteUrl(baseUrl, fromEmbeddedStyle)
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
            ?.let { href -> ParserUtils.toAbsoluteUrl(baseUrl, href) }
            .orEmpty()
    val watchingUrl =
        watchingNode
            ?.attr("href")
            .orEmpty()
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { href -> ParserUtils.toAbsoluteUrl(baseUrl, href) }
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
