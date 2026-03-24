package me.domino.fa2.ui.pages.submission

import me.domino.fa2.data.model.SubmissionThumbnail

private const val nextPrefetchCount = 3
private const val previousPrefetchCount = 1

/** 投稿左右滑页面状态。 */
sealed interface SubmissionPagerUiState {
  /** 空列表状态。 */
  data object Empty : SubmissionPagerUiState

  /**
   * 可浏览状态。
   *
   * @property submissions 当前可浏览列表。
   * @property detailBySid 按 sid 索引的详情状态。
   * @property currentIndex 当前索引。
   * @property hasPrevious 是否可上一条。
   * @property hasNext 是否可下一条。
   * @property hasMore 是否还有下一页。
   * @property isLoadingMore 是否正在追加。
   * @property appendErrorMessage 追加错误。
   */
  data class Data(
      /** 当前可浏览列表。 */
      val submissions: List<SubmissionThumbnail>,
      /** 详情状态映射。 */
      val detailBySid: Map<Int, SubmissionDetailUiState>,
      /** 当前索引。 */
      val currentIndex: Int,
      /** 是否可上一条。 */
      val hasPrevious: Boolean,
      /** 是否可下一条。 */
      val hasNext: Boolean,
      /** 是否存在下一页。 */
      val hasMore: Boolean,
      /** 是否正在加载下一页。 */
      val isLoadingMore: Boolean,
      /** 下一页加载错误。 */
      val appendErrorMessage: String?,
  ) : SubmissionPagerUiState
}

internal fun computeSubmissionPrefetchIndices(currentIndex: Int, lastIndex: Int): List<Int> {
  if (lastIndex < 0) return emptyList()
  val safeCurrentIndex = currentIndex.coerceIn(0, lastIndex)

  return buildList {
    for (offset in 1..nextPrefetchCount) {
      val nextIndex = safeCurrentIndex + offset
      if (nextIndex <= lastIndex) {
        add(nextIndex)
      }
    }

    for (offset in 1..previousPrefetchCount) {
      val previousIndex = safeCurrentIndex - offset
      if (previousIndex >= 0) {
        add(previousIndex)
      }
    }
  }
}
