package me.domino.fa2.ui.pages.submission

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.domino.fa2.application.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.util.logging.FaLog

/** 投稿详情浏览页面状态模型。 */
class SubmissionScreenModel(
    /** 初始投稿 ID。 */
    private val initialSid: Int,
    /** submission context id。 */
    private val contextId: String,
    /** 共享 submission context。 */
    private val contextScreenModel: SubmissionContextScreenModel,
    /** Submission 数据源。 */
    private val submissionSource: SubmissionPagerDetailSource,
    /** 描述/附件翻译编排服务。 */
    private val translationService: SubmissionDescriptionTranslationService,
    private val settingsService: AppSettingsService? = null,
    private val systemLanguageProvider: SystemLanguageProvider? = null,
) : StateScreenModel<SubmissionPagerUiState>(SubmissionPagerUiState.Empty) {
  private val log = FaLog.withTag("SubmissionScreenModel")
  private val mutablePageState =
      MutableStateFlow<PageState<SubmissionPagerUiState>>(PageState.Loading)
  val pageState: StateFlow<PageState<SubmissionPagerUiState>> = mutablePageState.asStateFlow()

  private val toastMessagesMutable = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val toastMessages: SharedFlow<String> = toastMessagesMutable.asSharedFlow()

  private val contextController =
      object : SubmissionPagerContextController {
        override fun initializeSelection(initialSid: Int) {
          contextScreenModel.selectSubmission(contextId, initialSid)
        }

        override fun size(): Int = contextScreenModel.snapshot(contextId)?.flatItems?.size ?: 0

        override fun currentIndex(): Int =
            contextScreenModel.snapshot(contextId)?.selectedFlatIndex ?: 0

        override fun current() =
            contextScreenModel.snapshot(contextId)?.flatItems?.getOrNull(currentIndex())

        override fun getAt(index: Int) =
            contextScreenModel.snapshot(contextId)?.flatItems?.getOrNull(index)

        override fun setCurrentIndex(index: Int) {
          val snapshot = contextScreenModel.snapshot(contextId) ?: return
          val target = snapshot.flatItems.getOrNull(index.coerceIn(0, snapshot.flatItems.lastIndex))
          if (target != null) {
            contextScreenModel.selectSubmission(contextId, target.id)
          }
        }

        override fun hasPreviousCached(): Boolean = currentIndex() > 0

        override fun hasNextCached(): Boolean {
          val snapshot = contextScreenModel.snapshot(contextId) ?: return false
          return currentIndex() < snapshot.flatItems.lastIndex
        }

        override fun hasMorePages(): Boolean =
            contextScreenModel.snapshot(contextId)?.hasNextPage == true

        override fun isLoadingMore(): Boolean =
            contextScreenModel.snapshot(contextId)?.loading?.appendLoading == true

        override fun appendErrorMessage(): String? =
            contextScreenModel.snapshot(contextId)?.loading?.appendErrorMessage

        override fun requestAppend(force: Boolean) {
          contextScreenModel.loadNextPageIfNeeded(contextId = contextId, force = force)
        }
      }

  private val workflow =
      SubmissionScreenWorkflow(
          initialSid = initialSid,
          contextController = contextController,
          submissionSource = submissionSource,
          translationService = translationService,
          settingsService = settingsService,
          systemLanguageProvider = systemLanguageProvider,
          screenModelScope = screenModelScope,
          log = log,
          stateSink = { mutableState.value = it },
          pageStateSink = { mutablePageState.value = it },
          toastSink = { message ->
            toastMessagesMutable.tryEmit(message)
            Unit
          },
      )

  init {
    workflow.initialize()
    screenModelScope.launch {
      contextScreenModel.state(contextId).filterNotNull().collect { workflow.onContextChanged() }
    }
  }

  fun previous() = workflow.previous()

  fun next() = workflow.next()

  fun onPageChanged(index: Int) = workflow.onPageChanged(index)

  fun setCurrentPageScrollOffset(sid: Int, offset: Int) =
      workflow.setCurrentPageScrollOffset(sid, offset)

  fun scrollOffsetForSid(sid: Int): Int = workflow.scrollOffsetForSid(sid)

  fun scrollToTopVersionForSid(sid: Int): Long = workflow.scrollToTopVersionForSid(sid)

  fun requestCurrentPageScrollToTop() = workflow.requestCurrentPageScrollToTop()

  fun retryCurrentDetail() = workflow.retryCurrentDetail()

  fun translateDescriptionCurrent() = workflow.translateDescriptionCurrent()

  fun toggleDescriptionWrapCurrent() = workflow.toggleDescriptionWrapCurrent()

  fun translateAttachmentCurrent() = workflow.translateAttachmentCurrent()

  fun toggleAttachmentWrapCurrent() = workflow.toggleAttachmentWrapCurrent()

  fun loadAttachmentTextCurrent() = workflow.loadAttachmentTextCurrent()

  fun toggleFavoriteCurrent() = workflow.toggleFavoriteCurrent()

  fun blockKeywordCurrent(tagName: String) = workflow.blockKeywordCurrent(tagName)

  fun retryLoadMore() = workflow.retryLoadMore()
}
