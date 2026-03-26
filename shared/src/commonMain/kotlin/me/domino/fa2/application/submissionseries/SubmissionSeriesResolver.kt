package me.domino.fa2.application.submissionseries

import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.delay
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.SubmissionDetailRepository
import me.domino.fa2.domain.translation.SubmissionDescriptionBlock
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockExtractor
import me.domino.fa2.domain.translation.SubmissionTranslationResultAligner
import me.domino.fa2.util.normalizeFaSubmissionUrl

internal const val submissionSeriesRequestThrottleMs: Long = 300L
private const val maxResolvedSeriesSize: Int = 20

class SubmissionSeriesResolver(private val repository: SubmissionDetailRepository) {
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
    val loader = SubmissionSeriesRemoteLoader(repository)
    val firstPair = resolveFirstPage(candidate, loader) ?: return null
    val (firstDetail, firstCandidate) = firstPair

    val resolvedDetails =
        if (firstCandidate.orderedSubmissionUrls.size >= 3) {
          resolveNumberedSeries(
              firstDetail = firstDetail,
              orderedSubmissionUrls = firstCandidate.orderedSubmissionUrls,
              loader = loader,
          )
        } else {
          resolveLinkedSeries(
              firstDetail = firstDetail,
              firstCandidate = firstCandidate,
              loader = loader,
          )
        }

    if (resolvedDetails.size < 3) return null
    return SubmissionSeriesResolvedSeries(
        candidateKey = candidate.candidateKey,
        firstSid = firstDetail.id,
        submissions = resolvedDetails,
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

    var previousUrl = candidate.previousSubmissionUrl ?: return null
    repeat(maxResolvedSeriesSize) {
      val previousDetail = loader.load(previousUrl) ?: return null
      val previousCandidate =
          detectCandidate(previousDetail.descriptionHtml, previousDetail.submissionUrl)
              ?: return null
      previousCandidate.firstSubmissionUrl?.let { firstUrl ->
        val firstDetail =
            if (normalizeUrl(firstUrl) == normalizeUrl(previousDetail.submissionUrl)) {
              previousDetail
            } else {
              loader.load(firstUrl) ?: return null
            }
        val firstCandidate =
            detectCandidate(firstDetail.descriptionHtml, firstDetail.submissionUrl) ?: return null
        return firstDetail to firstCandidate
      }
      previousUrl = previousCandidate.previousSubmissionUrl ?: return null
    }

    return null
  }

  private suspend fun resolveNumberedSeries(
      firstDetail: Submission,
      orderedSubmissionUrls: List<String>,
      loader: SubmissionSeriesRemoteLoader,
  ): List<Submission> {
    val resolved = mutableListOf(firstDetail)
    val visitedUrls = mutableSetOf(normalizeUrl(firstDetail.submissionUrl))
    orderedSubmissionUrls.drop(1).forEach { nextUrl ->
      if (resolved.size >= maxResolvedSeriesSize) return@forEach
      val normalizedUrl = normalizeUrl(nextUrl)
      if (!visitedUrls.add(normalizedUrl)) return@forEach
      val nextDetail = loader.load(nextUrl) ?: return@forEach
      resolved += nextDetail
    }
    return resolved
  }

  private suspend fun resolveLinkedSeries(
      firstDetail: Submission,
      firstCandidate: SubmissionSeriesCandidate,
      loader: SubmissionSeriesRemoteLoader,
  ): List<Submission> {
    val resolved = mutableListOf(firstDetail)
    val visitedUrls = mutableSetOf(normalizeUrl(firstDetail.submissionUrl))
    var currentCandidate = firstCandidate

    while (resolved.size < maxResolvedSeriesSize) {
      val nextUrl = currentCandidate.nextSubmissionUrl ?: break
      val normalizedNextUrl = normalizeUrl(nextUrl)
      if (!visitedUrls.add(normalizedNextUrl)) break

      val nextDetail = loader.load(nextUrl) ?: break
      resolved += nextDetail

      val nextCandidate =
          detectCandidate(nextDetail.descriptionHtml, nextDetail.submissionUrl) ?: break
      currentCandidate = nextCandidate
    }

    return resolved
  }
}

data class SubmissionSeriesResolvedSeries(
    val candidateKey: String,
    val firstSid: Int,
    val submissions: List<Submission>,
) {
  fun toSeedThumbnails(): List<SubmissionThumbnail> =
      submissions.map { detail ->
        SubmissionThumbnail(
            id = detail.id,
            submissionUrl = detail.submissionUrl,
            title = detail.title,
            author = detail.author,
            authorAvatarUrl = detail.authorAvatarUrl,
            thumbnailUrl = detail.previewImageUrl.ifBlank { detail.fullImageUrl },
            thumbnailAspectRatio = detail.aspectRatio,
            categoryTag = "",
        )
      }
}

data class SubmissionSeriesCandidate(
    val candidateKey: String,
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
    (listOf(rule.name, anchorBlockIndex.toString()) + urls.map(::normalizeUrl).sorted())
        .joinToString("::")

private fun normalizeLinkLabel(label: String): String =
    label.lowercase().replace(whitespaceRegex, " ").trim()

private fun normalizeUrl(url: String): String = url.trim().trimEnd('/')

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
) {
  private var hasLoaded: Boolean = false

  suspend fun load(url: String): Submission? {
    if (hasLoaded) {
      delay(submissionSeriesRequestThrottleMs)
    }
    hasLoaded = true
    return when (val state = repository.loadSubmissionDetailByUrl(url)) {
      is PageState.Success -> state.data
      PageState.CfChallenge,
      is PageState.MatureBlocked,
      is PageState.Error,
      PageState.Loading -> null
    }
  }
}
