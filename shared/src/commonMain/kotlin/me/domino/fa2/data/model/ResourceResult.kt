package me.domino.fa2.data.model

/** Terminal result of a data-resource request. Loading belongs to the UI that owns the request. */
sealed interface ResourceResult<out T> {
  /**
   * 加载成功。
   *
   * @property data 成功载荷。
   */
  data class Success<T>(
      /** 成功返回的数据。 */
      val data: T
  ) : ResourceResult<T>

  /**
   * 当前请求需要重新登录。
   *
   * @property requestUrl 原始请求地址。
   * @property message 展示文案。
   */
  data class AuthRequired(val requestUrl: String, val message: String) : ResourceResult<Nothing>

  /** 命中 Cloudflare challenge。 */
  data object CfChallenge : ResourceResult<Nothing>

  /**
   * 页面被 mature 规则拦截。
   *
   * @property reason 拦截说明。
   */
  data class MatureBlocked(
      /** mature 拦截原因文案。 */
      val reason: String
  ) : ResourceResult<Nothing>

  /**
   * 加载失败。
   *
   * @property exception 原始异常。
   */
  data class Error(
      /** 失败时携带的异常对象。 */
      val exception: Throwable
  ) : ResourceResult<Nothing>
}
