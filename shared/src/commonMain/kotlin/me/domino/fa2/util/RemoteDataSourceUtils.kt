package me.domino.fa2.util

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.HtmlResponseResult

/** 远端 HTML 响应到 PageState 的映射工具。 */
internal inline fun <T> HtmlResponseResult.toPageState(
  parseSuccess: (HtmlResponseResult.Success) -> T
): PageState<T> =
  when (this) {
    is HtmlResponseResult.Success ->
      runCatching { parseSuccess(this) }
        .fold(
          onSuccess = { data -> PageState.Success(data) },
          onFailure = { error -> PageState.Error(error) },
        )

    is HtmlResponseResult.CfChallenge -> PageState.CfChallenge
    is HtmlResponseResult.MatureBlocked -> PageState.MatureBlocked(reason)
    is HtmlResponseResult.Error -> PageState.Error(IllegalStateException(message))
  }
