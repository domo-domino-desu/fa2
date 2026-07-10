package me.domino.fa2.data.remote.fa.core

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** Owns the trust boundary for requests that may carry the FA authentication session. */
object FaOriginPolicy {
  private val pageHosts = setOf("furaffinity.net", "www.furaffinity.net")

  /** FA-owned hosts that may receive or mutate the shared FA cookie session. */
  fun isTrustedCookieOrigin(url: String): Boolean =
      parseHttpsUrl(url)?.host?.let(::isFuraffinityHost) == true

  /** Interactive HTML and action requests are restricted to the canonical web hosts. */
  fun isTrustedPageOrigin(url: String): Boolean = parseHttpsUrl(url)?.host?.lowercase() in pageHosts

  /** Accepts a parsed action only when both its origin and path are expected by the caller. */
  fun trustedActionUrlOrNull(url: String, allowedPathPrefixes: Set<String>): String? {
    val parsed = parseHttpsUrl(url) ?: return null
    if (parsed.host.lowercase() !in pageHosts) return null
    if (allowedPathPrefixes.none { prefix -> parsed.encodedPath.startsWith(prefix) }) return null
    return parsed.toString()
  }

  private fun isFuraffinityHost(host: String): Boolean {
    val normalized = host.lowercase()
    return normalized == "furaffinity.net" || normalized.endsWith(".furaffinity.net")
  }

  private fun parseHttpsUrl(raw: String): Url? {
    val normalized = raw.trim()
    if (normalized.isBlank()) return null
    val parsed = runCatching { Url(normalized) }.getOrNull() ?: return null
    return parsed.takeIf { url -> url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() }
  }
}
