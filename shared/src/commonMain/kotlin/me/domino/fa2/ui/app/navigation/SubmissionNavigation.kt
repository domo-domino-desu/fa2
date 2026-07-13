package me.domino.fa2.ui.app.navigation

import cafe.adriel.voyager.navigator.Navigator
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.domain.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.domain.submissionseries.SubmissionSeriesRule
import me.domino.fa2.ui.pages.submission.SubmissionRouteScreen

internal fun Navigator.openSubmissionFromList(sid: Int, contextId: String) {
  push(SubmissionRouteScreen(initialSid = sid, contextId = contextId))
}

internal fun Navigator.openSubmissionSeries(series: SubmissionSeriesResolvedSeries) {
  push(
      SubmissionRouteScreen(
          initialSid = series.firstSid,
          contextId = "submission-series:${series.candidateKey}:${nextSubmissionRouteNonce()}",
          seriesDescriptorJson =
              Json.encodeToString(
                  SubmissionSeriesDescriptor(
                      candidateKey = series.candidateKey,
                      firstSid = series.firstSid,
                      firstSubmissionUrl = series.firstSubmissionUrl,
                      previousRequestKey = series.previousRequestKey,
                      nextRequestKey = series.nextRequestKey,
                      rule = series.rule,
                      orderedSubmissionUrls = series.orderedSubmissionUrls,
                  )
              ),
      )
  )
}

@Serializable
internal data class SubmissionSeriesDescriptor(
    val candidateKey: String,
    val firstSid: Int,
    val firstSubmissionUrl: String,
    val previousRequestKey: String?,
    val nextRequestKey: String?,
    val rule: SubmissionSeriesRule,
    val orderedSubmissionUrls: List<String> = emptyList(),
) {
  fun toResolvedSeries(fallbackTitle: String): SubmissionSeriesResolvedSeries =
      SubmissionSeriesResolvedSeries(
          candidateKey = candidateKey,
          firstSid = firstSid,
          firstSubmissionUrl = firstSubmissionUrl,
          seedSubmissions =
              listOf(
                  SubmissionThumbnail(
                      id = firstSid,
                      submissionUrl = firstSubmissionUrl,
                      title = fallbackTitle,
                      author = "",
                      thumbnailUrl = "",
                      thumbnailAspectRatio = 1f,
                      categoryTag = "",
                  )
              ),
          previousRequestKey = previousRequestKey,
          nextRequestKey = nextRequestKey,
          rule = rule,
          orderedSubmissionUrls = orderedSubmissionUrls,
      )
}

internal fun decodeSubmissionSeriesDescriptor(value: String): SubmissionSeriesDescriptor =
    Json.decodeFromString(value)

private fun nextSubmissionRouteNonce(): Int = Random.nextInt(1, Int.MAX_VALUE)
