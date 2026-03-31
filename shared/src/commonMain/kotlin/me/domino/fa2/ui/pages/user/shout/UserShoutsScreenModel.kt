package me.domino.fa2.ui.pages.user.shout

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import me.domino.fa2.data.repository.UserRepository
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState

internal sealed interface UserShoutsUiState {
  data object Loading : UserShoutsUiState

  data class Error(val message: String) : UserShoutsUiState

  data class Success(val user: User) : UserShoutsUiState
}

internal class UserShoutsScreenModel(
    private val username: String,
    private val repository: UserRepository,
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
        is PageState.Success -> {
          mutableState.value = UserShoutsUiState.Success(next.data)
          log.i { "加载用户留言 -> ${summarizePageState(next)}" }
        }

        is PageState.AuthRequired -> {
          mutableState.value = UserShoutsUiState.Error(next.message)
          log.w { "加载用户留言 -> 需要重新登录" }
        }

        PageState.CfChallenge -> {
          mutableState.value = UserShoutsUiState.Error("Cloudflare verification required")
          log.w { "加载用户留言 -> Cloudflare验证" }
        }

        is PageState.MatureBlocked -> {
          mutableState.value = UserShoutsUiState.Error(next.reason)
          log.w { "加载用户留言 -> 受限(${next.reason})" }
        }

        is PageState.Error -> {
          mutableState.value =
              UserShoutsUiState.Error(next.exception.message ?: next.exception.toString())
          log.e(next.exception) { "加载用户留言 -> 失败" }
        }

        PageState.Loading -> Unit
      }
    }
  }
}
