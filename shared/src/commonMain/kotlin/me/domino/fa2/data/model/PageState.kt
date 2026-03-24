package me.domino.fa2.data.model

/** 页面统一状态模型。 */
sealed interface PageState<out T> {
  /** 加载中。 */
  data object Loading : PageState<Nothing>

  /**
   * 加载成功。
   *
   * @property data 成功载荷。
   */
  data class Success<T>(
      /** 成功返回的数据。 */
      val data: T
  ) : PageState<T>

  /** 命中 Cloudflare challenge。 */
  data object CfChallenge : PageState<Nothing>

  /**
   * 页面被 mature 规则拦截。
   *
   * @property reason 拦截说明。
   */
  data class MatureBlocked(
      /** mature 拦截原因文案。 */
      val reason: String
  ) : PageState<Nothing>

  /**
   * 加载失败。
   *
   * @property exception 原始异常。
   */
  data class Error(
      /** 失败时携带的异常对象。 */
      val exception: Throwable
  ) : PageState<Nothing>
}
