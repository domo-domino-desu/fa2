package me.domino.fa2.application.submissionseries

import com.fleeksoft.ksoup.Ksoup
import me.domino.fa2.application.request.SequentialRequestThrottle
import me.domino.fa2.application.request.defaultSequentialRequestThrottleMs
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.SubmissionDetailRepository
import me.domino.fa2.domain.translation.SubmissionDescriptionBlock
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockExtractor
import me.domino.fa2.domain.translation.SubmissionTranslationResultAligner
import me.domino.fa2.util.normalizeFaSubmissionUrl

internal const val submissionSeriesRequestThrottleMs: Long = defaultSequentialRequestThrottleMs
internal const val seriesInitialReadyCount: Int = 10
internal const val seriesWarmBufferCount: Int = 30
internal const val seriesBacktrackLimit: Int = 500

class SubmissionSeriesResolver(
    private val repository: SubmissionDetailRepository,
    private val requestThrottleMs: Long = submissionSeriesRequestThrottleMs,
) {
  private val blockExtractor =
      SubmissionDescriptionBlockExtractor(SubmissionTranslationResultAligner())

  fun detectCandidate(
      sourceHtml: String,
      baseUrl: String,
  ): SubmissionSeriesCandidate? = detectCandidate(blockExtractor.extract(sourceHtml), baseUrl)

  fun detectCandidate(
      sourceBlocks: List<SubmissionDescriptionBlock>,
      baseUrl: String,
  ): SubmissionSeriesCandidate? {
    if (sourceBlocks.isEmpty()) return null
    return detectPrevNextCandidate(sourceBlocks, baseUrl)
        ?: detectNumberedBlocksCandidate(sourceBlocks, baseUrl)
  }

  suspend fun resolveSeries(candidate: SubmissionSeriesCandidate): SubmissionSeriesResolvedSeries? {
    val loader =
        SubmissionSeriesRemoteLoader(
            repository = repository,
            throttle = SequentialRequestThrottle(requestThrottleMs),
        )
    val firstPair = resolveFirstPage(candidate, loader) ?: return null
    val (firstDetail, firstCandidate) = firstPair

    val seedResult =
        if (firstCandidate.orderedSubmissionUrls.size >= 3) {
          resolveNumberedSeriesSeed(
              firstDetail = firstDetail,
              orderedSubmissionUrls = firstCandidate.orderedSubmissionUrls,
              loader = loader,
          )
        } else {
          resolveLinkedSeriesSeed(
              firstDetail = firstDetail,
              firstCandidate = firstCandidate,
              loader = loader,
          )
        }

    if (seedResult.submissions.size < 3) return null
    return SubmissionSeriesResolvedSeries(
        candidateKey = candidate.candidateKey,
        firstSid = firstDetail.id,
        firstSubmissionUrl = firstDetail.submissionUrl,
        seedSubmissions = seedResult.submissions.map(Submission::toSubmissionThumbnail),
        previousRequestKey = seedResult.previousRequestKey,
        nextRequestKey = seedResult.nextRequestKey,
        rule = firstCandidate.rule,
        orderedSubmissionUrls =
            if (firstCandidate.rule == SubmissionSeriesRule.NUMBERED_BLOCKS) {
              firstCandidate.orderedSubmissionUrls
            } else {
              emptyList()
            },
    )
  }

  private suspend fun resolveFirstPage(
      candidate: SubmissionSeriesCandidate,
      loader: SubmissionSeriesRemoteLoader,
  ): Pair<Submission, SubmissionSeriesCandidate>? {
    candidate.firstSubmissionUrl?.let { firstUrl ->
      val firstDetail = loader.load(firstUrl) ?: return null
      val firstCandidate =
          detectCandidate(firstDetail.descriptionHtml, firstDetail.submissionUrl) ?: return null
      return firstDetail to firstCandidate
    }

    if (candidate.previousSubmissionUrl == null) {
      val currentUrl = candidate.currentSubmissionUrl
      val currentDetail = loader.load(currentUrl) ?: return null
      val currentCandidate =
          detectCandidate(currentDetail.descriptionHtml, currentDetail.submissionUrl) ?: return null
      if (currentCandidate.previousSubmissionUrl == null) {
        return currentDetail to currentCandidate
      }
    }

    var previousUrl = candidate.previousSubmissionUrl ?: return null
    val visitedUrls = mutableSetOf<String>()
    repeat(seriesBacktrackLimit) {
      val normalizedPreviousUrl = normalizeSubmissionSeriesUrl(previousUrl)
      if (!visitedUrls.add(normalizedPreviousUrl)) return null
      val previousDetail = loader.load(previousUrl) ?: return null
      val previousCandidate =
          detectCandidate(previousDetail.descriptionHtml, previousDetail.submissionUrl)
              ?: return null
      previousCandidate.firstSubmissionUrl?.let { firstUrl ->
        val firstDetail =
            if (
                normalizeSubmissionSeriesUrl(firstUrl) ==
                    normalizeSubmissionSeriesUrl(previousDetail.submissionUrl)
            ) {
              previousDetail
            } else {
              loader.load(firstUrl) ?: return null
            }
        val firstCandidate =
            detectCandidate(firstDetail.descriptionHtml, firstDetail.submissionUrl) ?: return null
        return firstDetail to firstCandidate
      }
      if (previousCandidate.previousSubmissionUrl == null) {
        return previousDetail to previousCandidate
      }
      previousUrl = previousCandidate.previousSubmissionUrl
    }

    return null
  }

  private suspend fun resolveNumberedSeriesSeed(
      firstDetail: Submission,
      orderedSubmissionUrls: List<String>,
      loader: SubmissionSeriesRemoteLoader,
  ): SubmissionSeriesSeedResult {
    val orderedUrls = orderedSubmissionUrls.distinctBy(::normalizeSubmissionSeriesUrl)
    val resolved = mutableListOf(firstDetail)
    val visitedUrls = mutableSetOf(normalizeSubmissionSeriesUrl(firstDetail.submissionUrl))
    var nextCursor =
        orderedUrls.indexOfFirst { url ->
          normalizeSubmissionSeriesUrl(url) ==
              normalizeSubmissionSeriesUrl(firstDetail.submissionUrl)
        }
    if (nextCursor < 0) nextCursor = 0
    nextCursor += 1

    while (nextCursor < orderedUrls.size && resolved.size < seriesInitialReadyCount) {
      val nextUrl = orderedUrls[nextCursor]
      val normalizedUrl = normalizeSubmissionSeriesUrl(nextUrl)
      if (normalizedUrl !in visitedUrls) {
        val nextDetail = loader.load(nextUrl) ?: break
        visitedUrls += normalizedUrl
        resolved += nextDetail
      }
      nextCursor += 1
    }

    val nextRequestKey =
        orderedUrls.drop(nextCursor).firstOrNull { url ->
          normalizeSubmissionSeriesUrl(url) !in visitedUrls
        }
    return SubmissionSeriesSeedResult(
        submissions = resolved,
        previousRequestKey = null,
        nextRequestKey = nextRequestKey,
    )
  }

  private suspend fun resolveLinkedSeriesSeed(
      firstDetail: Submission,
      firstCandidate: SubmissionSeriesCandidate,
      loader: SubmissionSeriesRemoteLoader,
  ): SubmissionSeriesSeedResult {
    val resolved = mutableListOf(firstDetail)
    val visitedUrls = mutableSetOf(normalizeSubmissionSeriesUrl(firstDetail.submissionUrl))
    var currentCandidate = firstCandidate
    var nextRequestKey =
        currentCandidate.nextSubmissionUrl?.takeIf { nextUrl ->
          normalizeSubmissionSeriesUrl(nextUrl) !in visitedUrls
        }

    while (resolved.size < seriesInitialReadyCount) {
      val nextUrl = nextRequestKey ?: break
      val normalizedNextUrl = normalizeSubmissionSeriesUrl(nextUrl)
      if (normalizedNextUrl in visitedUrls) {
        nextRequestKey = null
        break
      }

      val nextDetail = loader.load(nextUrl) ?: break
      visitedUrls += normalizedNextUrl
      resolved += nextDetail

      val nextCandidate =
          detectCandidate(nextDetail.descriptionHtml, nextDetail.submissionUrl) ?: break
      currentCandidate = nextCandidate
      nextRequestKey =
          currentCandidate.nextSubmissionUrl?.takeIf { candidateUrl ->
            normalizeSubmissionSeriesUrl(candidateUrl) !in visitedUrls
          }
    }

    return SubmissionSeriesSeedResult(
        submissions = resolved,
        previousRequestKey =
            firstCandidate.previousSubmissionUrl?.takeIf { previousUrl ->
              normalizeSubmissionSeriesUrl(previousUrl) !in visitedUrls
            },
        nextRequestKey = nextRequestKey,
    )
  }
}

data class SubmissionSeriesResolvedSeries(
    val candidateKey: String,
    val firstSid: Int,
    val firstSubmissionUrl: String,
    val seedSubmissions: List<SubmissionThumbnail>,
    val previousRequestKey: String?,
    val nextRequestKey: String?,
    val rule: SubmissionSeriesRule,
    val orderedSubmissionUrls: List<String> = emptyList(),
)

data class SubmissionSeriesCandidate(
    val candidateKey: String,
    val currentSubmissionUrl: String,
    val anchorBlockIndex: Int,
    val rule: SubmissionSeriesRule,
    val firstSubmissionUrl: String? = null,
    val previousSubmissionUrl: String? = null,
    val nextSubmissionUrl: String? = null,
    val orderedSubmissionUrls: List<String> = emptyList(),
)

enum class SubmissionSeriesRule {
  NUMBERED_BLOCKS,
  PREV_NEXT_BLOCK,
}

private data class BlockSubmissionLink(
    val label: String,
    val submissionUrl: String,
)

private data class NumberedBlockMatch(
    val blockIndex: Int,
    val partNumber: Int,
    val submissionUrl: String,
)

private data class SubmissionSeriesSeedResult(
    val submissions: List<Submission>,
    val previousRequestKey: String?,
    val nextRequestKey: String?,
)

private fun detectPrevNextCandidate(
    sourceBlocks: List<SubmissionDescriptionBlock>,
    baseUrl: String,
): SubmissionSeriesCandidate? {
  sourceBlocks.forEachIndexed { index, block ->
    val links = extractSubmissionLinks(block.originalHtml, baseUrl)
    if (links.isEmpty()) return@forEachIndexed

    var firstUrl: String? = null
    var previousUrl: String? = null
    var nextUrl: String? = null

    links.forEach { link ->
      when (classifyNavigationLink(link.label)) {
        SubmissionSeriesNavigationLinkType.FIRST ->
            if (firstUrl == null) firstUrl = link.submissionUrl

        SubmissionSeriesNavigationLinkType.PREVIOUS ->
            if (previousUrl == null) previousUrl = link.submissionUrl

        SubmissionSeriesNavigationLinkType.NEXT -> if (nextUrl == null) nextUrl = link.submissionUrl
        SubmissionSeriesNavigationLinkType.OTHER -> Unit
      }
    }

    if (previousUrl != null || nextUrl != null) {
      return SubmissionSeriesCandidate(
          candidateKey =
              buildCandidateKey(
                  rule = SubmissionSeriesRule.PREV_NEXT_BLOCK,
                  anchorBlockIndex = index,
                  urls = listOfNotNull(firstUrl, previousUrl, nextUrl),
              ),
          currentSubmissionUrl = baseUrl,
          anchorBlockIndex = index,
          rule = SubmissionSeriesRule.PREV_NEXT_BLOCK,
          firstSubmissionUrl = firstUrl,
          previousSubmissionUrl = previousUrl,
          nextSubmissionUrl = nextUrl,
      )
    }
  }
  return null
}

private fun detectNumberedBlocksCandidate(
    sourceBlocks: List<SubmissionDescriptionBlock>,
    baseUrl: String,
): SubmissionSeriesCandidate? {
  val matches =
      sourceBlocks.mapIndexed { index, block -> detectNumberedBlockMatch(index, block, baseUrl) }
  var cursor = 0
  while (cursor < matches.size) {
    val currentMatch = matches[cursor]
    if (currentMatch == null) {
      cursor += 1
      continue
    }

    val run = mutableListOf(currentMatch)
    var nextIndex = cursor + 1
    while (nextIndex < matches.size) {
      val nextMatch = matches[nextIndex] ?: break
      run += nextMatch
      nextIndex += 1
    }

    val orderedByPart = run.associateBy { it.partNumber }
    val partOne = orderedByPart[1]
    if (run.size >= 3 && partOne != null && orderedByPart.keys.size >= 3) {
      val orderedUrls =
          orderedByPart.toSortedMap().values.map { match -> match.submissionUrl }.distinct()
      return SubmissionSeriesCandidate(
          candidateKey =
              buildCandidateKey(
                  rule = SubmissionSeriesRule.NUMBERED_BLOCKS,
                  anchorBlockIndex = partOne.blockIndex,
                  urls = orderedUrls,
              ),
          currentSubmissionUrl = baseUrl,
          anchorBlockIndex = partOne.blockIndex,
          rule = SubmissionSeriesRule.NUMBERED_BLOCKS,
          firstSubmissionUrl = partOne.submissionUrl,
          orderedSubmissionUrls = orderedUrls,
      )
    }

    cursor = nextIndex
  }
  return null
}

private fun detectNumberedBlockMatch(
    blockIndex: Int,
    block: SubmissionDescriptionBlock,
    baseUrl: String,
): NumberedBlockMatch? {
  val submissionUrl =
      extractSubmissionLinks(block.originalHtml, baseUrl).firstOrNull()?.submissionUrl
          ?: return null
  val partNumber =
      partNumberRegex.find(block.sourceText)?.groupValues?.drop(1)?.firstNotNullOfOrNull {
        it.takeIf(String::isNotBlank)?.toIntOrNull()
      } ?: return null
  return NumberedBlockMatch(
      blockIndex = blockIndex,
      partNumber = partNumber,
      submissionUrl = submissionUrl,
  )
}

private fun extractSubmissionLinks(html: String, baseUrl: String): List<BlockSubmissionLink> {
  val body = Ksoup.parseBodyFragment(html).body()
  return body
      .select("a[href]")
      .mapNotNull { element ->
        val submissionUrl =
            normalizeFaSubmissionUrl(baseUrl, element.attr("href")) ?: return@mapNotNull null
        val label = element.text().trim()
        BlockSubmissionLink(label = label, submissionUrl = submissionUrl)
      }
      .distinctBy { link -> "${normalizeLinkLabel(link.label)}@@${link.submissionUrl}" }
}

private fun buildCandidateKey(
    rule: SubmissionSeriesRule,
    anchorBlockIndex: Int,
    urls: List<String>,
): String =
    (listOf(rule.name, anchorBlockIndex.toString()) +
            urls.map(::normalizeSubmissionSeriesUrl).sorted())
        .joinToString("::")

private fun normalizeLinkLabel(label: String): String =
    label.lowercase().replace(whitespaceRegex, " ").trim()

internal fun normalizeSubmissionSeriesUrl(url: String): String = url.trim().trimEnd('/')

private val whitespaceRegex = Regex("""\s+""")
private val nonLettersRegex = Regex("""[^a-z]+""")
private val partNumberRegex =
    Regex("""\b(?:part\s*0*([1-9]\d*)|p\s*0*([1-9]\d*))\b""", RegexOption.IGNORE_CASE)

private fun classifyNavigationLink(label: String): SubmissionSeriesNavigationLinkType {
  val normalized = normalizeLinkLabel(label)
  val compact = normalized.replace(nonLettersRegex, "")
  return when {
    "first" in compact -> SubmissionSeriesNavigationLinkType.FIRST
    "prev" in compact -> SubmissionSeriesNavigationLinkType.PREVIOUS
    "next" in compact -> SubmissionSeriesNavigationLinkType.NEXT
    else -> SubmissionSeriesNavigationLinkType.OTHER
  }
}

private enum class SubmissionSeriesNavigationLinkType {
  FIRST,
  PREVIOUS,
  NEXT,
  OTHER,
}

private class SubmissionSeriesRemoteLoader(
    private val repository: SubmissionDetailRepository,
    private val throttle: SequentialRequestThrottle,
) {
  suspend fun load(url: String): Submission? {
    throttle.awaitReady()
    return when (val state = repository.loadSubmissionDetailByUrl(url)) {
      is PageState.Success -> state.data
      PageState.CfChallenge,
      is PageState.MatureBlocked,
      is PageState.Error,
      PageState.Loading -> null
    }
  }
}

internal fun Submission.toSubmissionThumbnail(): SubmissionThumbnail =
    SubmissionThumbnail(
        id = id,
        submissionUrl = submissionUrl,
        title = title,
        author = author,
        authorAvatarUrl = authorAvatarUrl,
        thumbnailUrl = previewImageUrl.ifBlank { fullImageUrl },
        thumbnailAspectRatio = aspectRatio,
        categoryTag = "",
    )
