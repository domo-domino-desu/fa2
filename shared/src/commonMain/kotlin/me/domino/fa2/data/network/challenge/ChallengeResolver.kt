package me.domino.fa2.data.network.challenge

/** Cloudflare challenge 触发信息。 */
data class CfChallengeSignal(
    /** 触发 challenge 的请求地址。 */
    val requestUrl: String,
    /** Cloudflare 响应中的 cf-ray（可能为空）。 */
    val cfRay: String?,
)

/** challenge 处理器：请求命中 challenge 后由上层协调解题并返回是否可重试。 */
fun interface ChallengeResolver {
  /**
   * 等待 challenge 处理结果。
   *
   * @return true 表示可重试原请求；false 表示用户取消或校验失败。
   */
  suspend fun awaitResolution(challenge: CfChallengeSignal): Boolean
}
