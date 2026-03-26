package me.domino.fa2.ui.pages.submission

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import me.domino.fa2.application.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.util.logging.FaLog

/** 投稿详情浏览页面状态模型。 */
class SubmissionScreenModel(
    /** 初始投稿 ID。 */
    private val initialSid: Int,
    /** 投稿列表共享持有器。 */
    private val holder: SubmissionListHolder,
    /** Feed 数据源。 */
    private val feedSource: SubmissionPagerFeedSource,
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

  private val workflow =
      SubmissionScreenWorkflow(
          initialSid = initialSid,
          holder = holder,
          feedSource = feedSource,
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
