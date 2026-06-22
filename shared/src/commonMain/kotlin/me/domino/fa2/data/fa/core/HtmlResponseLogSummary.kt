package me.domino.fa2.data.fa.core

/** HtmlResponseResult 中文摘要。 */
fun summarizeHtmlResult(result: HtmlResponseResult): String =
    when (result) {
      is HtmlResponseResult.Success -> "成功"
      is HtmlResponseResult.AuthRequired -> "需要登录:${result.requestUrl}"
      is HtmlResponseResult.CfChallenge -> {
        val ray = result.cfRay?.takeIf { it.isNotBlank() } ?: "-"
        "Cloudflare验证(cf-ray=$ray)"
      }

      HtmlResponseResult.ChallengeAborted -> "验证已取消"
      is HtmlResponseResult.MatureBlocked -> "受限:${result.reason}"
      is HtmlResponseResult.Error -> "HTTP${result.statusCode}:${result.message}"
    }
