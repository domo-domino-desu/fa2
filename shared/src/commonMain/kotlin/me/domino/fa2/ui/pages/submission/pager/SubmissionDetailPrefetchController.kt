package me.domino.fa2.ui.pages.submission.pager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.domain.submissionviewer.api.SubmissionPagerDetailSource

/** Owns debounce, deduplication, and lifecycle for adjacent submission detail prefetches. */
internal class SubmissionDetailPrefetchController(
    private val scope: CoroutineScope,
    private val source: SubmissionPagerDetailSource,
    private val candidateSids: () -> List<Int>,
    private val onLoaded: (sid: Int, submission: Submission) -> Unit,
) {
  private var scheduleJob: Job? = null
  private val successfulSids = mutableSetOf<Int>()
  private val activeSids = mutableSetOf<Int>()

  fun markLoaded(sid: Int) {
    successfulSids += sid
  }

  fun schedule() {
    scheduleJob?.cancel()
    val targets = candidateSids()
    if (targets.isEmpty()) return

    scheduleJob =
        scope.launch {
          delay(pagerPrefetchDebounceMs)
          targets.forEach { sid ->
            if (sid in successfulSids || !activeSids.add(sid)) return@forEach
            launch {
              try {
                val result = source.loadBySid(sid)
                if (result is ResourceResult.Success) {
                  markLoaded(sid)
                  onLoaded(sid, result.data)
                }
              } finally {
                activeSids -= sid
              }
            }
          }
        }
  }
}
