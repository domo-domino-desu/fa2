package me.domino.fa2.data.fa.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** 管理 Cloudflare 挑战会话生命周期及 UI 状态的存储器。 */
class ChallengeSessionStorage {
  /** 日志标签。 */
  private val log = FaLog.withTag("ChallengeSessionStorage")

  /** 保护会话状态并发访问的互斥锁。 */
  private val mutex = Mutex()

  /** 当前活跃的挑战会话，无挑战时为 null。 */
  private var active: ActiveChallengeSession? = null

  /** 可变的挑战 UI 状态流。 */
  private val mutableState = MutableStateFlow<CfChallengeUiState>(CfChallengeUiState.Idle)

  /** 对外暴露的只读挑战 UI 状态流。 */
  val state: StateFlow<CfChallengeUiState> = mutableState.asStateFlow()

  /** 获取当前挑战会话，若不存在则创建新会话。 */
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

  /** 返回当前活跃会话，若无则返回 null。 */
  suspend fun currentSession(): ActiveChallengeSession? = mutex.withLock { active }

  /** 将指定会话状态更新为"验证中"。 */
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

  /** 将指定会话状态更新为"验证失败"。 */
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

  /** 完成挑战会话并清除活跃状态，返回已完成的会话对象。 */
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

/** 获取挑战会话的结果，含会话对象与是否为新建标志。 */
data class ChallengeSessionAcquisition(
    /** 获取到的会话对象。 */
    val session: ActiveChallengeSession,
    /** 是否为新建的会话（false 表示复用已有会话）。 */
    val created: Boolean,
)

/** 当前活跃的 Cloudflare 挑战会话。 */
data class ActiveChallengeSession(
    /** 触发挑战的原始请求 URL。 */
    val triggerUrl: String,
    /** CF-Ray 请求追踪 ID，可能为空。 */
    val cfRay: String?,
    /** 挑战完成时用于通知等待方的 Deferred。 */
    val deferred: CompletableDeferred<Boolean>,
)
