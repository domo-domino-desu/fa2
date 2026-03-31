package me.domino.fa2.ui.pages.submission

import me.domino.fa2.data.model.SubmissionThumbnail

internal interface SubmissionPagerContextController {
  fun initializeSelection(initialSid: Int)

  fun sourceKind(): SubmissionContextSourceKind?

  fun size(): Int

  fun currentIndex(): Int

  fun current(): SubmissionThumbnail?

  fun getAt(index: Int): SubmissionThumbnail?

  fun setCurrentIndex(index: Int)

  fun hasPreviousCached(): Boolean

  fun hasPreviousPages(): Boolean

  fun hasNextCached(): Boolean

  fun hasMorePages(): Boolean

  fun isLoadingMore(): Boolean

  fun appendErrorMessage(): String?

  fun requestPrepend(force: Boolean)

  fun requestAppend(force: Boolean)
}
