package me.domino.fa2.util.logging

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.HtmlResponseResult

/**
 * PageState 中文摘要。
 */
fun summarizePageState(state: PageState<*>): String =
    when (state) {
        PageState.Loading -> "加载中"
        PageState.CfChallenge -> "Cloudflare 验证"
        is PageState.Success<*> -> "成功"
        is PageState.MatureBlocked -> "受限:${state.reason}"
        is PageState.Error -> "失败:${summarizeThrowable(state.exception)}"
    }

/**
 * HtmlResponseResult 中文摘要。
 */
fun summarizeHtmlResult(result: HtmlResponseResult): String =
    when (result) {
        is HtmlResponseResult.Success -> "成功"
        is HtmlResponseResult.CfChallenge -> {
            val ray = result.cfRay?.takeIf { it.isNotBlank() } ?: "-"
            "Cloudflare验证(cf-ray=$ray)"
        }

        is HtmlResponseResult.MatureBlocked -> "受限:${result.reason}"
        is HtmlResponseResult.Error -> "HTTP${result.statusCode}:${result.message}"
    }

/**
 * URL 输出摘要（中度脱敏，保留 URL）。
 */
fun summarizeUrl(url: String): String = url.trim()

private fun summarizeThrowable(throwable: Throwable): String =
    throwable.message?.takeIf { it.isNotBlank() } ?: throwable.toString()
