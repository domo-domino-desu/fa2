package me.domino.fa2.util

/** 将底层请求异常转换成可直接展示给用户的消息。 */
internal fun Throwable.toUserFacingRequestMessage(): String {
  val summary =
      buildList {
            this += this@toUserFacingRequestMessage::class.simpleName.orEmpty()
            this += this@toUserFacingRequestMessage.message.orEmpty()
          }
          .joinToString(separator = " ")
          .lowercase()

  return when {
    "unknownhostexception" in summary ||
        "unable to resolve host" in summary ||
        "no address associated with hostname" in summary ||
        "eai_nodata" in summary ->
        "Unable to resolve host. Check your network connection and retry."

    "sockettimeoutexception" in summary ||
        "connect timeout" in summary ||
        "timed out" in summary ||
        "timeout" in summary -> "Connection timed out. Check your network and retry."

    "connectexception" in summary ||
        "failed to connect" in summary ||
        "network is unreachable" in summary ||
        "connection reset" in summary ->
        "Unable to connect to Fur Affinity. Check your network and retry."

    else ->
        message?.trim()?.takeIf { it.isNotBlank() }
            ?: "Request failed. Please check your network and retry."
  }
}
