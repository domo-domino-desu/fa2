package me.domino.fa2.data.model

/** ResourceResult 中文摘要。 */
fun summarizeResourceResult(state: ResourceResult<*>): String =
    when (state) {
      is ResourceResult.AuthRequired -> "需要登录:${state.requestUrl}"
      ResourceResult.CfChallenge -> "Cloudflare 验证"
      is ResourceResult.Success<*> -> "成功"
      is ResourceResult.MatureBlocked -> "受限:${state.reason}"
      is ResourceResult.Error -> "失败:${summarizeThrowable(state.exception)}"
    }

private fun summarizeThrowable(throwable: Throwable): String =
    throwable.message?.takeIf { it.isNotBlank() } ?: throwable.toString()
