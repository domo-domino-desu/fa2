package me.domino.fa2.ui.navigation

import androidx.compose.ui.platform.UriHandler
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import kotlin.random.Random
import me.domino.fa2.ui.pages.submission.SubmissionRouteScreen
import me.domino.fa2.ui.pages.user.journal.JournalDetailRouteScreen
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.parseJournalId
import me.domino.fa2.util.parseSubmissionSid
import me.domino.fa2.util.toAbsoluteUrl

/** FA 站内链接在应用内打开（push 新页面）； 其他链接透传给系统处理器。 */
class FaLinkUriHandler(private val navigator: Navigator, private val fallback: UriHandler) :
    UriHandler {
  override fun openUri(uri: String) {
    val normalized = toAbsoluteUrl(FaUrls.home, uri)
    val target = parseFaRouteTarget(normalized)
    if (target == null) {
      fallback.openUri(normalized)
      return
    }
    navigator.push(target)
  }
}

internal fun parseFaRouteTarget(url: String): Screen? {
  if (!isFaUrl(url)) return null
  val path = extractPath(url)

  val submissionSid = parseSubmissionSid(url)
  if (submissionSid != null) {
    return SubmissionRouteScreen(
        initialSid = submissionSid,
        contextId = "submission-direct:${submissionSid}:${nextFaRouteNonce()}",
        seedSubmissionUrl = url,
    )
  }

  val journalId = parseJournalId(url)
  if (journalId != null) {
    return JournalDetailRouteScreen(journalId = journalId, journalUrl = url)
  }

  return when {
    path.startsWith("/gallery/") ->
        extractUsername(path, "/gallery/")?.let { username ->
          UserRouteScreen(
              username = username,
              initialChildRoute = UserChildRoute.Gallery,
              initialFolderUrl = extractInitialFolderUrl(path),
          )
        }

    path.startsWith("/favorites/") ->
        extractUsername(path, "/favorites/")?.let { username ->
          UserRouteScreen(
              username = username,
              initialChildRoute = UserChildRoute.Favorites,
              initialFolderUrl = extractInitialFolderUrl(path),
          )
        }

    path.startsWith("/scraps/") ->
        extractUsername(path, "/scraps/")?.let { username ->
          UserRouteScreen(
              username = username,
              initialChildRoute = UserChildRoute.Gallery,
              initialFolderUrl = extractInitialScrapsUrl(url, path),
          )
        }

    path.startsWith("/journals/") ->
        extractUsername(path, "/journals/")?.let { username ->
          UserRouteScreen(username = username, initialChildRoute = UserChildRoute.Journals)
        }

    path.startsWith("/user/") ->
        extractUsername(path, "/user/")?.let { username ->
          UserRouteScreen(username = username, initialChildRoute = UserChildRoute.Gallery)
        }

    else -> null
  }
}

private fun isFaUrl(url: String): Boolean {
  val normalized = url.trim().lowercase()
  return normalized.startsWith("https://www.furaffinity.net/") ||
      normalized.startsWith("http://www.furaffinity.net/") ||
      normalized.startsWith("https://furaffinity.net/") ||
      normalized.startsWith("http://furaffinity.net/")
}

private fun extractPath(url: String): String {
  val trimmed = url.trim()
  val afterScheme = trimmed.substringAfter("://", missingDelimiterValue = trimmed)
  val withLeadingSlash = "/" + afterScheme.substringAfter('/', missingDelimiterValue = "")
  return withLeadingSlash.substringBefore('#').substringBefore('?')
}

private fun extractUsername(path: String, prefix: String): String? =
    path.removePrefix(prefix).substringBefore('/').trim().takeIf { it.isNotBlank() }

private fun extractInitialFolderUrl(path: String): String? =
    path
        .takeIf { value -> value.contains("/folder/") }
        ?.let { relative -> toAbsoluteUrl(FaUrls.home, relative) }

private fun extractInitialScrapsUrl(url: String, path: String): String =
    url.trim().takeIf { it.isNotBlank() } ?: toAbsoluteUrl(FaUrls.home, path)

private fun nextFaRouteNonce(): Int = Random.nextInt(1, Int.MAX_VALUE)
