package me.domino.fa2.data.network.endpoint

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.network.challenge.CfChallengeSignal
import me.domino.fa2.data.network.challenge.ChallengeResolver

/**
 * 社交动作端点（Fav/Watch 等）。
 */
class SocialActionEndpoint(
    private val dataSource: FaHtmlDataSource? = null,
    private val client: HttpClient? = null,
    private val cookiesStorage: FaCookiesStorage? = null,
    private val userAgentStorage: UserAgentStorage? = null,
    private val challengeResolver: ChallengeResolver? = null,
) {
    private val json by lazy { Json { ignoreUnknownKeys = true } }

    constructor(
        dataSource: FaHtmlDataSource,
    ) : this(
        dataSource = dataSource,
        client = null,
        cookiesStorage = null,
        userAgentStorage = null,
        challengeResolver = null,
    )

    constructor(
        client: HttpClient,
        cookiesStorage: FaCookiesStorage,
        userAgentStorage: UserAgentStorage,
        challengeResolver: ChallengeResolver,
    ) : this(
        dataSource = null,
        client = client,
        cookiesStorage = cookiesStorage,
        userAgentStorage = userAgentStorage,
        challengeResolver = challengeResolver,
    )

    /**
     * 执行动作 URL。
     */
    suspend fun execute(actionUrl: String): SocialActionResult {
        val targetUrl = actionUrl.trim()
        if (targetUrl.isBlank()) {
            return SocialActionResult.Failed(
                message = "Empty social action url",
            )
        }

        val availableClient = client
        val availableCookiesStorage = cookiesStorage
        val availableUserAgentStorage = userAgentStorage
        if (availableClient != null && availableCookiesStorage != null && availableUserAgentStorage != null) {
            return executeByRawHttp(
                actionUrl = targetUrl,
                client = availableClient,
                cookiesStorage = availableCookiesStorage,
                userAgentStorage = availableUserAgentStorage,
            )
        }

        val availableDataSource = dataSource
            ?: return SocialActionResult.Failed("No HTTP backend for social action")
        return when (val response = availableDataSource.get(targetUrl)) {
            is HtmlResponseResult.Success -> SocialActionResult.Completed(redirected = false)
            is HtmlResponseResult.CfChallenge -> {
                val resolver = challengeResolver
                if (resolver == null) {
                    SocialActionResult.Challenge(cfRay = response.cfRay)
                } else {
                    val resolved = resolver.awaitResolution(
                        challenge = CfChallengeSignal(
                            requestUrl = targetUrl,
                            cfRay = response.cfRay,
                        ),
                    )
                    if (!resolved) {
                        SocialActionResult.Failed("Cloudflare challenge unresolved")
                    } else {
                        when (val retried = availableDataSource.get(targetUrl)) {
                            is HtmlResponseResult.Success -> SocialActionResult.Completed(redirected = false)
                            is HtmlResponseResult.MatureBlocked -> SocialActionResult.Blocked(retried.reason)
                            is HtmlResponseResult.Error -> SocialActionResult.Failed(retried.message)
                            is HtmlResponseResult.CfChallenge -> SocialActionResult.Challenge(cfRay = retried.cfRay)
                        }
                    }
                }
            }

            is HtmlResponseResult.MatureBlocked -> SocialActionResult.Blocked(response.reason)
            is HtmlResponseResult.Error -> SocialActionResult.Failed(response.message)
        }
    }

    /**
     * 屏蔽/取消屏蔽标签（POST /route/tag_blocking）。
     */
    suspend fun updateTagBlocklist(
        tagName: String,
        nonce: String,
        toAdd: Boolean = true,
    ): SocialActionResult {
        val normalizedTagName = tagName.trim()
        val normalizedNonce = nonce.trim()
        if (normalizedTagName.isBlank()) {
            return SocialActionResult.Failed(
                message = "Empty tag name for tag blocking",
            )
        }
        if (normalizedNonce.isBlank()) {
            return SocialActionResult.Failed(
                message = "Missing tag block nonce",
            )
        }

        val availableClient = client
        val availableCookiesStorage = cookiesStorage
        val availableUserAgentStorage = userAgentStorage
        if (availableClient == null || availableCookiesStorage == null || availableUserAgentStorage == null) {
            return SocialActionResult.Failed("No HTTP backend for tag blocking")
        }

        return executeTagBlockingByRawHttp(
            tagName = normalizedTagName,
            nonce = normalizedNonce,
            toAdd = toAdd,
            client = availableClient,
            cookiesStorage = availableCookiesStorage,
            userAgentStorage = availableUserAgentStorage,
            challengeRetryCount = 0,
            rateLimitedRetryCount = 0,
        )
    }

    private suspend fun executeByRawHttp(
        actionUrl: String,
        client: HttpClient,
        cookiesStorage: FaCookiesStorage,
        userAgentStorage: UserAgentStorage,
        challengeRetryCount: Int = 0,
    ): SocialActionResult {
        userAgentStorage.loadPersistedIfNeeded()
        val cookieHeader = cookiesStorage.loadRawCookieHeader()
        val userAgent = userAgentStorage.currentUserAgent()

        val response = client.get(actionUrl) {
            if (cookieHeader.isNotBlank()) {
                header(HttpHeaders.Cookie, cookieHeader)
            }
            header(HttpHeaders.UserAgent, userAgent)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        }

        val statusCode = response.status.value
        val setCookieValues = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        cookiesStorage.mergeSetCookieValues(setCookieValues)

        if (statusCode in setOf(301, 302, 303, 307, 308)) {
            return SocialActionResult.Completed(redirected = true)
        }

        val body = response.bodyAsText()
        val headers = response.headers.entries().associate { (key, values) -> key to values }
        return when (
            val classified = HtmlResponseResult.classify(
                statusCode = statusCode,
                headers = headers,
                body = body,
                url = actionUrl,
            )
        ) {
            is HtmlResponseResult.Success -> SocialActionResult.Completed(redirected = false)
            is HtmlResponseResult.CfChallenge -> {
                val resolver = challengeResolver
                if (resolver == null) {
                    SocialActionResult.Challenge(cfRay = classified.cfRay)
                } else {
                    val resolved = resolver.awaitResolution(
                        challenge = CfChallengeSignal(
                            requestUrl = actionUrl,
                            cfRay = classified.cfRay,
                        ),
                    )
                    if (!resolved) {
                        SocialActionResult.Failed("Cloudflare challenge unresolved")
                    } else if (challengeRetryCount >= 1) {
                        SocialActionResult.Challenge(cfRay = classified.cfRay)
                    } else {
                        executeByRawHttp(
                            actionUrl = actionUrl,
                            client = client,
                            cookiesStorage = cookiesStorage,
                            userAgentStorage = userAgentStorage,
                            challengeRetryCount = challengeRetryCount + 1,
                        )
                    }
                }
            }

            is HtmlResponseResult.MatureBlocked -> SocialActionResult.Blocked(classified.reason)
            is HtmlResponseResult.Error -> SocialActionResult.Failed(classified.message)
        }
    }

    private suspend fun executeTagBlockingByRawHttp(
        tagName: String,
        nonce: String,
        toAdd: Boolean,
        client: HttpClient,
        cookiesStorage: FaCookiesStorage,
        userAgentStorage: UserAgentStorage,
        challengeRetryCount: Int,
        rateLimitedRetryCount: Int,
    ): SocialActionResult {
        val action = if (toAdd) "add-tag" else "remove-tag"
        val tagBlockingUrl = "https://www.furaffinity.net/route/tag_blocking"

        userAgentStorage.loadPersistedIfNeeded()
        val cookieHeader = cookiesStorage.loadRawCookieHeader()
        val userAgent = userAgentStorage.currentUserAgent()
        val nonceFingerprint = "len=${nonce.length},hash=${nonce.hashCode().toUInt().toString(16)}"

        val response = client.post(tagBlockingUrl) {
            if (cookieHeader.isNotBlank()) {
                header(HttpHeaders.Cookie, cookieHeader)
            }
            header(HttpHeaders.UserAgent, userAgent)
            header(HttpHeaders.Accept, "application/json,text/plain,*/*")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("action", action)
                        append("key", nonce)
                        append("tag_name", tagName)
                    },
                ),
            )
        }

        val statusCode = response.status.value
        val setCookieValues = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        cookiesStorage.mergeSetCookieValues(setCookieValues)
        val body = response.bodyAsText()

        if (statusCode in setOf(301, 302, 303, 307, 308)) {
            return SocialActionResult.Completed(redirected = true)
        }

        val jsonObject = runCatching {
            json.parseToJsonElement(body).jsonObject
        }.getOrNull()

        if (jsonObject != null) {
            val success = jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true
            if (success) {
                return SocialActionResult.Completed(redirected = false)
            }

            val error = jsonObject["error"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (error.equals("rate-limited", ignoreCase = true) && rateLimitedRetryCount < 2) {
                val timeLeftSeconds = jsonObject["time-left"]?.jsonPrimitive?.contentOrNull
                    ?.toFloatOrNull()
                    ?.coerceAtLeast(0f)
                    ?: 0.5f
                val delayMs = (timeLeftSeconds * 1000).toLong().coerceIn(200L, 3_000L)
                delay(delayMs)
                return executeTagBlockingByRawHttp(
                    tagName = tagName,
                    nonce = nonce,
                    toAdd = toAdd,
                    client = client,
                    cookiesStorage = cookiesStorage,
                    userAgentStorage = userAgentStorage,
                    challengeRetryCount = challengeRetryCount,
                    rateLimitedRetryCount = rateLimitedRetryCount + 1,
                )
            }

            val message = jsonObject["message"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: error.ifBlank { "Tag blocking failed" }
            return SocialActionResult.Failed(message)
        }

        val headers = response.headers.entries().associate { (key, values) -> key to values }
        return when (
            val classified = HtmlResponseResult.classify(
                statusCode = statusCode,
                headers = headers,
                body = body,
                url = tagBlockingUrl,
            )
        ) {
            is HtmlResponseResult.Success -> SocialActionResult.Completed(redirected = false)
            is HtmlResponseResult.CfChallenge -> {
                val resolver = challengeResolver
                if (resolver == null) {
                    SocialActionResult.Challenge(cfRay = classified.cfRay)
                } else {
                    val resolved = resolver.awaitResolution(
                        challenge = CfChallengeSignal(
                            requestUrl = tagBlockingUrl,
                            cfRay = classified.cfRay,
                        ),
                    )
                    if (!resolved) {
                        SocialActionResult.Failed("Cloudflare challenge unresolved")
                    } else if (challengeRetryCount >= 1) {
                        SocialActionResult.Challenge(cfRay = classified.cfRay)
                    } else {
                        executeTagBlockingByRawHttp(
                            tagName = tagName,
                            nonce = nonce,
                            toAdd = toAdd,
                            client = client,
                            cookiesStorage = cookiesStorage,
                            userAgentStorage = userAgentStorage,
                            challengeRetryCount = challengeRetryCount + 1,
                            rateLimitedRetryCount = rateLimitedRetryCount,
                        )
                    }
                }
            }

            is HtmlResponseResult.MatureBlocked -> SocialActionResult.Blocked(classified.reason)
            is HtmlResponseResult.Error -> SocialActionResult.Failed(classified.message)
        }
    }

    private fun bodyPreview(
        body: String,
        maxLength: Int = 220,
    ): String {
        val normalized = body.replace('\n', ' ').replace('\r', ' ').trim()
        if (normalized.isBlank()) return "<empty>"
        return if (normalized.length > maxLength) {
            normalized.take(maxLength) + "..."
        } else {
            normalized
        }
    }
}

/**
 * 社交动作执行结果。
 */
sealed interface SocialActionResult {
    data class Completed(
        val redirected: Boolean,
    ) : SocialActionResult

    data class Challenge(
        val cfRay: String?,
    ) : SocialActionResult

    data class Blocked(
        val reason: String,
    ) : SocialActionResult

    data class Failed(
        val message: String,
    ) : SocialActionResult
}
