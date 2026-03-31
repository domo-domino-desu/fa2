package me.domino.fa2.application.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 待恢复的 FA 站内路由存储。 */
class PendingFaRouteStore {
  private val mutablePendingUri = MutableStateFlow<String?>(null)
  val pendingUri: StateFlow<String?> = mutablePendingUri.asStateFlow()

  fun save(uri: String) {
    val normalized = uri.trim()
    if (normalized.isBlank()) return
    mutablePendingUri.value = normalized
  }

  fun peek(): String? = mutablePendingUri.value

  fun consume(uri: String) {
    val current = mutablePendingUri.value ?: return
    if (current == uri) {
      mutablePendingUri.value = null
    }
  }

  fun clear() {
    mutablePendingUri.value = null
  }
}
