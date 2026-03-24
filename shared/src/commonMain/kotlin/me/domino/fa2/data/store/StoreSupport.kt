package me.domino.fa2.data.store

import kotlin.time.Clock
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.PageState
import org.mobilenativefoundation.store.store5.StoreReadResponse

internal data object CfChallengeException : IllegalStateException("Cloudflare challenge detected")

internal data class MatureBlockedException(
    val reason: String,
) : IllegalStateException(reason)

internal class RemoteRequestException(message: String) : IllegalStateException(message)

internal fun <T> PageState<T>.requireStoreValue(): T =
    when (this) {
        is PageState.Success -> data
        PageState.CfChallenge -> throw CfChallengeException
        is PageState.MatureBlocked -> throw MatureBlockedException(reason)
        is PageState.Error -> throw exception
        PageState.Loading -> throw IllegalStateException("Unexpected loading state in fetcher")
    }

internal fun <T> mapStoreException(error: Throwable): PageState<T> =
    when (error) {
        CfChallengeException -> PageState.CfChallenge
        is MatureBlockedException -> PageState.MatureBlocked(error.reason)
        else -> PageState.Error(error)
    }

internal fun <T> toPageState(response: StoreReadResponse<T>): PageState<T>? =
    when (response) {
        is StoreReadResponse.Loading,
        StoreReadResponse.Initial,
        is StoreReadResponse.NoNewData -> null

        is StoreReadResponse.Data -> PageState.Success(response.value)
        is StoreReadResponse.Error.Exception -> mapStoreException(response.error)
        is StoreReadResponse.Error.Message -> PageState.Error(IllegalStateException(response.message))
        is StoreReadResponse.Error.Custom<*> -> PageState.Error(IllegalStateException(response.error.toString()))
    }

internal fun <T> isTerminalResponse(response: StoreReadResponse<T>): Boolean =
    when (response) {
        is StoreReadResponse.Loading,
        StoreReadResponse.Initial -> false

        is StoreReadResponse.Data,
        is StoreReadResponse.NoNewData,
        is StoreReadResponse.Error.Exception,
        is StoreReadResponse.Error.Message,
        is StoreReadResponse.Error.Custom<*> -> true
    }

internal inline fun <T> readCacheIfValid(
    entity: PageCacheEntity?,
    expectedPageType: String,
    decode: (PageCacheEntity) -> T?,
): T? =
    entity
        ?.takeIf { cached -> cached.pageType == expectedPageType }
        ?.takeIf { cached -> isCacheFresh(cached) }
        ?.let(decode)

private fun isCacheFresh(entity: PageCacheEntity): Boolean {
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val ageMs = nowMs - entity.cachedAtMs
    return ageMs in 0..ttlForPageType(entity.pageType)
}

private fun ttlForPageType(pageType: String): Long =
    when (pageType) {
        "feed_page_v1",
        "browse_page_v1",
        "search_page_v1",
        "gallery_page_v1",
        "favorites_page_v1",
        "journals_page_v1",
        "watchlist_page_v1" -> SHORT_TTL_MS

        "user_header_v1",
        "submission_detail_v1",
        "journal_detail_v1" -> LONG_TTL_MS

        else -> SHORT_TTL_MS
    }

private const val SHORT_TTL_MS: Long = 3 * 60 * 1000L
private const val LONG_TTL_MS: Long = 30 * 60 * 1000L
