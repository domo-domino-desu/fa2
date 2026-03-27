package me.domino.fa2.ui.navigation

import cafe.adriel.voyager.navigator.Navigator
import kotlin.random.Random
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.ui.pages.submission.SubmissionRouteScreen

internal fun Navigator.openSubmissionFromList(
    sid: Int,
    contextId: String,
) {
  push(SubmissionRouteScreen(initialSid = sid, contextId = contextId))
}

internal fun Navigator.openSubmissionSeries(series: SubmissionSeriesResolvedSeries) {
  push(
      SubmissionRouteScreen(
          initialSid = series.firstSid,
          contextId = "submission-series:${series.candidateKey}:${nextSubmissionRouteNonce()}",
          seedSubmissions = series.toSeedThumbnails(),
          seedSeriesKey = series.candidateKey,
      )
  )
}

private fun nextSubmissionRouteNonce(): Int = Random.nextInt(1, Int.MAX_VALUE)
