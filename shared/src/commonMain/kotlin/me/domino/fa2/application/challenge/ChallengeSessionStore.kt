package me.domino.fa2.application.challenge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.domain.challenge.CfChallengeSignal
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

class ChallengeSessionStore {
  private val log = FaLog.withTag("ChallengeSessionStore")
  private val mutex = Mutex()
  private var active: ActiveChallengeSession? = null
  private val mutableState = MutableStateFlow<CfChallengeUiState>(CfChallengeUiState.Idle)
  val state: StateFlow<CfChallengeUiState> = mutableState.asStateFlow()

  suspend fun acquire(challenge: CfChallengeSignal): ChallengeSessionAcquisition =
      mutex.withLock {
        val current = active
        if (current != null) {
          log.d {
            "Challenge会话存储 -> 复用(triggerUrl=${summarizeUrl(current.triggerUrl)},cf-ray=${current.cfRay ?: "-"})"
          }
          return@withLock ChallengeSessionAcquisition(session = current, created = false)
        }

        val created =
            ActiveChallengeSession(
                triggerUrl = challenge.requestUrl,
                cfRay = challenge.cfRay,
                deferred = CompletableDeferred(),
            )
        active = created
        mutableState.value =
            CfChallengeUiState.Active(
                triggerUrl = created.triggerUrl,
                cfRay = created.cfRay,
                status = CfChallengeStatus.AwaitingUserAction,
            )
        log.i {
          "Challenge会话存储 -> 创建(triggerUrl=${summarizeUrl(created.triggerUrl)},cf-ray=${created.cfRay ?: "-"})"
        }
        ChallengeSessionAcquisition(session = created, created = true)
      }

  suspend fun currentSession(): ActiveChallengeSession? = mutex.withLock { active }

  suspend fun markVerifying(session: ActiveChallengeSession) {
    mutex.withLock {
      if (active !== session) return
      mutableState.value =
          CfChallengeUiState.Active(
              triggerUrl = session.triggerUrl,
              cfRay = session.cfRay,
              status = CfChallengeStatus.Verifying,
          )
      log.d { "Challenge会话存储 -> 标记验证中(triggerUrl=${summarizeUrl(session.triggerUrl)})" }
    }
  }

  suspend fun markVerificationFailed(session: ActiveChallengeSession, detail: String?) {
    mutex.withLock {
      if (active !== session) return
      mutableState.value =
          CfChallengeUiState.Active(
              triggerUrl = session.triggerUrl,
              cfRay = session.cfRay,
              status = CfChallengeStatus.VerificationFailed(detail = detail),
          )
      log.w {
        "Challenge会话存储 -> 标记验证失败(triggerUrl=${summarizeUrl(session.triggerUrl)},detail=${detail ?: "-"})"
      }
    }
  }

  suspend fun complete(result: Boolean): ActiveChallengeSession? =
      mutex.withLock {
        val session = active
        active = null
        mutableState.value = CfChallengeUiState.Idle
        session?.deferred?.complete(result)
        if (session != null) {
          log.i {
            "Challenge会话存储 -> 完成(triggerUrl=${summarizeUrl(session.triggerUrl)},result=${if (result) "成功" else "失败"})"
          }
        }
        session
      }
}

data class ChallengeSessionAcquisition(
    val session: ActiveChallengeSession,
    val created: Boolean,
)

data class ActiveChallengeSession(
    val triggerUrl: String,
    val cfRay: String?,
    val deferred: CompletableDeferred<Boolean>,
)
