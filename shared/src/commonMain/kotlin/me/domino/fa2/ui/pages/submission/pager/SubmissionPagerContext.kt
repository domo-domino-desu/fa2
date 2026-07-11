package me.domino.fa2.ui.pages.submission.pager

import me.domino.fa2.domain.submissionviewer.api.SubmissionContextSourceKind

internal class SubmissionPagerContext(
    private val contextId: String,
    private val contexts: SubmissionContextScreenModel,
) {
  fun initializeSelection(sid: Int) = contexts.selectSubmission(contextId, sid)

  fun sourceKind(): SubmissionContextSourceKind? = snapshot()?.sourceKind

  fun size(): Int = snapshot()?.flatItems?.size ?: 0

  fun currentIndex(): Int = snapshot()?.selectedFlatIndex ?: 0

  fun current() = snapshot()?.flatItems?.getOrNull(currentIndex())

  fun getAt(index: Int) = snapshot()?.flatItems?.getOrNull(index)

  fun setCurrentIndex(index: Int) {
    val state = snapshot() ?: return
    state.flatItems.getOrNull(index.coerceIn(0, state.flatItems.lastIndex))?.let {
      contexts.selectSubmission(contextId, it.id)
    }
  }

  fun hasPreviousCached(): Boolean = currentIndex() > 0

  fun hasPreviousPages(): Boolean = snapshot()?.hasPreviousPage == true

  fun hasNextCached(): Boolean = currentIndex() < ((snapshot()?.flatItems?.lastIndex) ?: -1)

  fun hasMorePages(): Boolean = snapshot()?.hasNextPage == true

  fun isLoadingMore(): Boolean = snapshot()?.loading?.appendLoading == true

  fun appendErrorMessage(): String? = snapshot()?.loading?.appendErrorMessage

  fun requestPrepend(force: Boolean) = contexts.loadPreviousPageIfNeeded(contextId, force)

  fun requestAppend(force: Boolean) = contexts.loadNextPageIfNeeded(contextId, force)

  private fun snapshot() = contexts.snapshot(contextId)
}
