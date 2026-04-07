package me.domino.fa2.application.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

/** 待恢复的 FA 站内路由存储。 */
class PendingFaRouteStore {
  private val log = FaLog.withTag("PendingFaRouteStore")
  private val mutablePendingUri = MutableStateFlow<String?>(null)
  val pendingUri: StateFlow<String?> = mutablePendingUri.asStateFlow()

  fun save(uri: String) {
    val normalized = uri.trim()
    if (normalized.isBlank()) {
      log.w { "保存待恢复路由 -> 跳过(空URI)" }
      return
    }
    mutablePendingUri.value = normalized
    log.i { "保存待恢复路由 -> uri=${summarizeUrl(normalized)}" }
  }

  fun peek(): String? = mutablePendingUri.value

  fun consume(uri: String) {
    val current = mutablePendingUri.value ?: return
    if (current == uri) {
      mutablePendingUri.value = null
      log.i { "消费待恢复路由 -> uri=${summarizeUrl(uri)}" }
    }
  }

  fun clear() {
    if (mutablePendingUri.value != null) {
      log.i { "清理待恢复路由 -> 成功" }
    }
    mutablePendingUri.value = null
  }
}
