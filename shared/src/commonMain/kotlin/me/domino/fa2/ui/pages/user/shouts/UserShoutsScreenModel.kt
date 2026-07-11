package me.domino.fa2.ui.pages.user.shouts

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.User
import me.domino.fa2.data.remote.fa.user.UserRepository

internal class UserShoutsScreenModel(
    private val username: String,
    private val repository: UserRepository,
) : StateScreenModel<QueryState<User>>(QueryState.Loading()) {
  private var queryJob: Job? = null

  init {
    load()
  }

  fun load(forceRefresh: Boolean = false) {
    if (queryJob?.isActive != true) {
      queryJob =
          screenModelScope.launch { repository.user(username).collect { mutableState.value = it } }
    }
    if (forceRefresh) screenModelScope.launch { repository.refreshUser(username) }
  }
}
