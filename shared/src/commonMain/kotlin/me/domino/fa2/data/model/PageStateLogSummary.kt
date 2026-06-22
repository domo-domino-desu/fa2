package me.domino.fa2.data.model

/** PageState 中文摘要。 */
fun summarizePageState(state: PageState<*>): String =
    when (state) {
      PageState.Loading -> "加载中"
      is PageState.AuthRequired -> "需要登录:${state.requestUrl}"
      PageState.CfChallenge -> "Cloudflare 验证"
      is PageState.Success<*> -> "成功"
      is PageState.MatureBlocked -> "受限:${state.reason}"
      is PageState.Error -> "失败:${summarizeThrowable(state.exception)}"
    }

private fun summarizeThrowable(throwable: Throwable): String =
    throwable.message?.takeIf { it.isNotBlank() } ?: throwable.toString()
