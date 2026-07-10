package me.domino.fa2.data.model

/** 登录态探测结果。 */
sealed interface AuthProbeResult {
  enum class InvalidReason {
    SessionExpired,
    ChallengePending,
    ChallengeAborted,
    AccessBlocked,
  }

  /**
   * 已登录，允许进入业务页面。
   *
   * @property username 解析出的用户名，可能为空（页面结构不足时）。
   */
  data class LoggedIn(
      /** 当前用户名，可能为空。 */
      val username: String?
  ) : AuthProbeResult

  /**
   * 认证无效，必须输入或更新 Cookie。
   *
   * @property reason 可供 UI 本地化的原因。
   */
  data class AuthInvalid(
      val reason: InvalidReason,
      /** 受限页等服务端提供的动态详情。 */
      val detail: String? = null,
  ) : AuthProbeResult

  /**
   * 登录探测过程发生错误。
   *
   * @property message 错误摘要。
   */
  data class ProbeFailed(
      /** 登录探测失败时的错误摘要。 */
      val message: String
  ) : AuthProbeResult
}
