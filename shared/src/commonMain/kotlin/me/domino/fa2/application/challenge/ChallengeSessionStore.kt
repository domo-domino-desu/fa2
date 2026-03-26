package me.domino.fa2.application.challenge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.application.challenge.port.CfChallengeSignal

class ChallengeSessionStore {
  private val mutex = Mutex()
  private var active: ActiveChallengeSession? = null
  private val mutableState = MutableStateFlow<CfChallengeUiState>(CfChallengeUiState.Idle)
  val state: StateFlow<CfChallengeUiState> = mutableState.asStateFlow()

  suspend fun acquire(challenge: CfChallengeSignal): ChallengeSessionAcquisition =
      mutex.withLock {
        val current = active
        if (current != null) {
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
    }
  }

  suspend fun complete(result: Boolean): ActiveChallengeSession? =
      mutex.withLock {
        val session = active
        active = null
        mutableState.value = CfChallengeUiState.Idle
        session?.deferred?.complete(result)
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
