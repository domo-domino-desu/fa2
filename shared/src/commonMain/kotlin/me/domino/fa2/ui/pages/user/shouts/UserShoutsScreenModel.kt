package me.domino.fa2.ui.pages.user.shouts

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.User
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.data.remote.fa.user.UserResourceRepository
import me.domino.fa2.utils.logging.FaLog

internal sealed interface UserShoutsUiState {
  data object Loading : UserShoutsUiState

  data class Error(val message: String) : UserShoutsUiState

  data class Success(val user: User) : UserShoutsUiState
}

internal class UserShoutsScreenModel(
    private val username: String,
    private val repository: UserResourceRepository,
) : StateScreenModel<UserShoutsUiState>(UserShoutsUiState.Loading) {
  private val log = FaLog.withTag("UserShoutsScreenModel")

  init {
    load()
  }

  fun load(forceRefresh: Boolean = false) {
    log.i { "加载用户留言 -> 开始(user=$username,forceRefresh=$forceRefresh)" }
    mutableState.value = UserShoutsUiState.Loading
    screenModelScope.launch {
      when (
          val next =
              if (forceRefresh) repository.refreshUser(username) else repository.loadUser(username)
      ) {
        is ResourceResult.Success -> {
          mutableState.value = UserShoutsUiState.Success(next.data)
          log.i { "加载用户留言 -> ${summarizeResourceResult(next)}" }
        }

        is ResourceResult.AuthRequired -> {
          mutableState.value = UserShoutsUiState.Error(next.message)
          log.w { "加载用户留言 -> 需要重新登录" }
        }

        ResourceResult.CfChallenge -> {
          mutableState.value = UserShoutsUiState.Error("Cloudflare verification required")
          log.w { "加载用户留言 -> Cloudflare验证" }
        }

        is ResourceResult.MatureBlocked -> {
          mutableState.value = UserShoutsUiState.Error(next.reason)
          log.w { "加载用户留言 -> 受限(${next.reason})" }
        }

        is ResourceResult.Error -> {
          mutableState.value =
              UserShoutsUiState.Error(next.exception.message ?: next.exception.toString())
          log.e(next.exception) { "加载用户留言 -> 失败" }
        }
      }
    }
  }
}
