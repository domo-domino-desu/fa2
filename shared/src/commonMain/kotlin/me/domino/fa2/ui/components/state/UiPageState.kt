package me.domino.fa2.ui.components.state

import me.domino.fa2.data.model.ResourceResult

/** UI-owned page lifecycle. Data results are terminal and are mapped into this state explicitly. */
sealed interface UiPageState<out T> {
  data object Loading : UiPageState<Nothing>

  data class Success<T>(val data: T) : UiPageState<T>

  data class AuthRequired(val requestUrl: String, val message: String) : UiPageState<Nothing>

  data object CfChallenge : UiPageState<Nothing>

  data class MatureBlocked(val reason: String) : UiPageState<Nothing>

  data class Error(val exception: Throwable) : UiPageState<Nothing>
}

fun <T> ResourceResult<T>.toUiPageState(): UiPageState<T> =
    when (this) {
      is ResourceResult.Success -> UiPageState.Success(data)
      is ResourceResult.AuthRequired -> UiPageState.AuthRequired(requestUrl, message)
      ResourceResult.CfChallenge -> UiPageState.CfChallenge
      is ResourceResult.MatureBlocked -> UiPageState.MatureBlocked(reason)
      is ResourceResult.Error -> UiPageState.Error(exception)
    }
