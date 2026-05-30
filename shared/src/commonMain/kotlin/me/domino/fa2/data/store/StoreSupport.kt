package me.domino.fa2.data.store

import kotlin.time.Clock
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.PageState
import org.mobilenativefoundation.store.store5.StoreReadResponse

/** 检测到 Cloudflare 挑战时抛出的异常。 */
internal data object CfChallengeException : IllegalStateException("Cloudflare challenge detected")

/** 请求需要登录认证时抛出的异常。 */
internal data class AuthRequiredException(
    /** 触发认证要求的请求地址。 */
    val requestUrl: String,
    /** 详细说明。 */
    val detail: String,
) : IllegalStateException(detail)

/** 内容因成人审核被屏蔽时抛出的异常。 */
internal data class MatureBlockedException(val reason: String) : IllegalStateException(reason)

/** 远程请求失败时抛出的通用异常。 */
internal class RemoteRequestException(message: String) : IllegalStateException(message)

/** 从 PageState 中取出成功数据，否则将状态转换为对应异常并抛出。 */
internal fun <T> PageState<T>.requireStoreValue(): T =
    when (this) {
      is PageState.Success -> data
      is PageState.AuthRequired -> throw AuthRequiredException(requestUrl, message)
      PageState.CfChallenge -> throw CfChallengeException
      is PageState.MatureBlocked -> throw MatureBlockedException(reason)
      is PageState.Error -> throw exception
      PageState.Loading -> throw IllegalStateException("Unexpected loading state in fetcher")
    }

/** 将已知的 Store 异常映射为对应的 PageState 错误状态。 */
internal fun <T> mapStoreException(error: Throwable): PageState<T> =
    when (error) {
      CfChallengeException -> PageState.CfChallenge
      is AuthRequiredException -> PageState.AuthRequired(error.requestUrl, error.detail)
      is MatureBlockedException -> PageState.MatureBlocked(error.reason)
      else -> PageState.Error(error)
    }

/** 将 StoreReadResponse 转换为 PageState，Loading/Initial/NoNewData 返回 null。 */
internal fun <T> toPageState(response: StoreReadResponse<T>): PageState<T>? =
    when (response) {
      is StoreReadResponse.Loading,
      StoreReadResponse.Initial,
      is StoreReadResponse.NoNewData -> null

      is StoreReadResponse.Data -> PageState.Success(response.value)
      is StoreReadResponse.Error.Exception -> mapStoreException(response.error)
      is StoreReadResponse.Error.Message -> PageState.Error(IllegalStateException(response.message))
      is StoreReadResponse.Error.Custom<*> ->
          PageState.Error(IllegalStateException(response.error.toString()))
    }

/** 判断 StoreReadResponse 是否为终态（不再有后续响应）。 */
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

/** 若缓存存在且类型与 TTL 均有效则解码并返回，否则返回 null。 */
internal inline fun <T> readCacheIfValid(
    entity: PageCacheEntity?,
    expectedPageType: String,
    decode: (PageCacheEntity) -> T?,
): T? =
    entity
        ?.takeIf { cached -> cached.pageType == expectedPageType }
        ?.takeIf { cached -> isCacheFresh(cached) }
        ?.let(decode)

/** 判断缓存实体是否在有效期内。 */
private fun isCacheFresh(entity: PageCacheEntity): Boolean {
  val nowMs = Clock.System.now().toEpochMilliseconds()
  val ageMs = nowMs - entity.cachedAtMs
  return ageMs in 0..ttlForPageType(entity.pageType)
}

/** 根据页面类型返回对应的缓存 TTL（毫秒）。 */
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
      "user_header_v2",
      "submission_detail_v1",
      "journal_detail_v1" -> LONG_TTL_MS

      else -> SHORT_TTL_MS
    }

/** 短缓存 TTL（3 分钟），用于列表类页面。 */
private const val SHORT_TTL_MS: Long = 3 * 60 * 1000L

/** 长缓存 TTL（30 分钟），用于详情类页面。 */
private const val LONG_TTL_MS: Long = 30 * 60 * 1000L
