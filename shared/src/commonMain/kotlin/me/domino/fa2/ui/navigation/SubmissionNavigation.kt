package me.domino.fa2.ui.navigation

import cafe.adriel.voyager.navigator.Navigator
import kotlin.random.Random
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.ui.pages.submission.SubmissionRouteScreen

internal fun Navigator.openSubmissionFromList(
    sid: Int,
    holderTag: String = "submission-list-holder",
    onSelect: (Int) -> Unit,
) {
  onSelect(sid)
  push(SubmissionRouteScreen(initialSid = sid, holderTag = holderTag))
}

internal fun Navigator.openSubmissionSeries(series: SubmissionSeriesResolvedSeries) {
  push(
      SubmissionRouteScreen(
          initialSid = series.firstSid,
          holderTag = "submission-series:${series.candidateKey}:${nextSubmissionRouteNonce()}",
          seedSubmissions = series.toSeedThumbnails(),
          seedSeriesKey = series.candidateKey,
      )
  )
}

private fun nextSubmissionRouteNonce(): Int = Random.nextInt(1, Int.MAX_VALUE)
