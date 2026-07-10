package me.domino.fa2.data.fa.submission

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission

/** Data-layer contract required by the submission-series domain workflow. */
interface SubmissionSeriesSubmissionSource {
  suspend fun loadSubmissionDetailBySid(sid: Int): ResourceResult<Submission>

  suspend fun loadSubmissionDetailByUrl(url: String): ResourceResult<Submission>
}
