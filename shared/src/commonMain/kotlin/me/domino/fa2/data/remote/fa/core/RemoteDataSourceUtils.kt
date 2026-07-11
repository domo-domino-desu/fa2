package me.domino.fa2.data.remote.fa.core

import me.domino.fa2.data.model.ResourceResult

/** 远端 HTML 响应到 ResourceResult 的映射工具。 */
internal inline fun <T> HtmlResponseResult.toResourceResult(
    parseSuccess: (HtmlResponseResult.Success) -> T
): ResourceResult<T> =
    when (this) {
      is HtmlResponseResult.Success ->
          runCatching { parseSuccess(this) }
              .fold(
                  onSuccess = { data -> ResourceResult.Success(data) },
                  onFailure = { error -> ResourceResult.Error(error) },
              )

      is HtmlResponseResult.AuthRequired ->
          ResourceResult.AuthRequired(requestUrl = requestUrl, message = message)
      is HtmlResponseResult.CfChallenge -> ResourceResult.CfChallenge
      HtmlResponseResult.ChallengeAborted -> ResourceResult.CfChallenge
      is HtmlResponseResult.MatureBlocked -> ResourceResult.MatureBlocked(reason)
      is HtmlResponseResult.Error -> ResourceResult.Error(IllegalStateException(message))
    }

internal suspend inline fun <T> FaHtmlDataSource.fetchParsed(
    url: String,
    parse: (html: String, finalUrl: String) -> T,
): ResourceResult<T> = get(url).toResourceResult { parse(it.body, it.url) }
