package me.domino.fa2.domain.submissionseries

import com.fleeksoft.ksoup.Ksoup
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.domain.translation.SubmissionDescriptionBlock
import me.domino.fa2.utils.concurrency.SequentialRequestThrottle
import me.domino.fa2.utils.html.HtmlTextBlock
import me.domino.fa2.utils.html.HtmlTextBlockExtractor
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl
import me.domino.fa2.utils.normalizeFaSubmissionUrl

/** 投稿串联请求节流时间间隔（毫秒）。 */
internal const val submissionSeriesRequestThrottleMs: Long = 600L

interface SubmissionSeriesSubmissionSource {
  suspend fun loadSubmissionDetailBySid(sid: Int): PageState<Submission>

  suspend fun loadSubmissionDetailByUrl(url: String): PageState<Submission>
}

/** 串联首屏初始预加载数量。 */
internal const val seriesInitialReadyCount: Int = 10

/** 串联预热缓冲数量。 */
internal const val seriesWarmBufferCount: Int = 30

/** 向前追溯最大跳数，防止死循环。 */
internal const val seriesBacktrackLimit: Int = 500

/** 解析投稿描述中的串联结构，并加载串联投稿列表。 */
class SubmissionSeriesService(
    private val repository: SubmissionSeriesSubmissionSource,
    private val requestThrottleMs: Long = submissionSeriesRequestThrottleMs,
) {
  /** 日志标签。 */
  private val log = FaLog.withTag("SubmissionSeriesService")

  /** 用于从描述 HTML 中提取区块的解析器。 */
  private val blockExtractor = HtmlTextBlockExtractor()

  /** 从描述 HTML 中检测串联候选，返回候选对象或 null。 */
  fun detectCandidate(
      sourceHtml: String,
      baseUrl: String,
  ): SubmissionSeriesCandidate? =
      detectCandidate(
              blockExtractor.extract(sourceHtml).map(HtmlTextBlock::toDescriptionBlock),
              baseUrl,
          )
          .also { candidate ->
            log.d { "投稿串联 -> 解析候选(baseUrl=${summarizeUrl(baseUrl)},found=${candidate != null})" }
          }

  /** 从已提取的区块列表中检测串联候选。 */
  fun detectCandidate(
      sourceBlocks: List<SubmissionDescriptionBlock>,
      baseUrl: String,
  ): SubmissionSeriesCandidate? {
    if (sourceBlocks.isEmpty()) {
      log.d { "投稿串联 -> 跳过候选解析(空区块,baseUrl=${summarizeUrl(baseUrl)})" }
      return null
    }
    return (detectPrevNextCandidate(sourceBlocks, baseUrl)
            ?: detectNumberedBlocksCandidate(sourceBlocks, baseUrl))
        ?.also { candidate ->
          log.d { "投稿串联 -> 命中候选(rule=${candidate.rule},baseUrl=${summarizeUrl(baseUrl)})" }
        }
  }

  /** 根据候选信息完整解析串联并返回结果，失败时返回 null。 */
  suspend fun resolveSeries(candidate: SubmissionSeriesCandidate): SubmissionSeriesResolvedSeries? {
    log.i {
      "投稿串联 -> 开始解析(rule=${candidate.rule},currentUrl=${summarizeUrl(candidate.currentSubmissionUrl)})"
    }
    val loader =
        SubmissionSeriesRemoteLoader(
            repository = repository,
            throttle = SequentialRequestThrottle(requestThrottleMs),
        )
    val firstPair =
        resolveFirstPage(candidate, loader)
            ?: return null.also {
              log.w { "投稿串联 -> 首条解析失败(currentUrl=${summarizeUrl(candidate.currentSubmissionUrl)})" }
            }
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

    if (seedResult.submissions.size < 3) {
      log.w { "投稿串联 -> 结果不足(rule=${firstCandidate.rule},count=${seedResult.submissions.size})" }
      return null
    }
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
        .also { series ->
          log.i {
            "投稿串联 -> 解析成功(rule=${series.rule},seedCount=${series.seedSubmissions.size},firstSid=${series.firstSid})"
          }
        }
  }

  /** 向前追溯找到串联第一条投稿，返回其详情与候选对，失败返回 null。 */
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

  /** 按编号顺序加载串联初始投稿列表（用于 NUMBERED_BLOCKS 规则）。 */
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

  /** 通过 prev/next 链接链式加载串联初始投稿列表（用于 PREV_NEXT_BLOCK 规则）。 */
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

/** 完整解析后的投稿串联结果。 */
data class SubmissionSeriesResolvedSeries(
    /** 对应的候选 Key，用于缓存命中判断。 */
    val candidateKey: String,
    /** 串联第一条投稿的 sid。 */
    val firstSid: Int,
    /** 串联第一条投稿的 URL。 */
    val firstSubmissionUrl: String,
    /** 初始加载的缩略图列表（种子数据）。 */
    val seedSubmissions: List<SubmissionThumbnail>,
    /** 加载前一页的请求 Key，无则为 null。 */
    val previousRequestKey: String?,
    /** 加载下一页的请求 Key，无则为 null。 */
    val nextRequestKey: String?,
    /** 命中的串联规则。 */
    val rule: SubmissionSeriesRule,
    /** 按顺序排列的全部投稿 URL（仅 NUMBERED_BLOCKS 规则填充）。 */
    val orderedSubmissionUrls: List<String> = emptyList(),
)

/** 从投稿描述中检测到的串联候选信息。 */
data class SubmissionSeriesCandidate(
    /** 候选的唯一标识 Key。 */
    val candidateKey: String,
    /** 当前投稿的 URL。 */
    val currentSubmissionUrl: String,
    /** 命中的锚点区块索引。 */
    val anchorBlockIndex: Int,
    /** 命中的源码文本行末偏移，用于把操作按钮贴到具体行末；未知时为 null。 */
    val anchorTextLineEndOffset: Int? = null,
    /** 命中的串联规则。 */
    val rule: SubmissionSeriesRule,
    /** 串联第一条投稿 URL，可能为 null。 */
    val firstSubmissionUrl: String? = null,
    /** 上一条投稿 URL，可能为 null。 */
    val previousSubmissionUrl: String? = null,
    /** 下一条投稿 URL，可能为 null。 */
    val nextSubmissionUrl: String? = null,
    /** 按顺序排列的全部投稿 URL（仅 NUMBERED_BLOCKS 规则填充）。 */
    val orderedSubmissionUrls: List<String> = emptyList(),
)

/** 投稿串联检测规则枚举。 */
enum class SubmissionSeriesRule {
  /** 通过编号段落列表识别串联。 */
  NUMBERED_BLOCKS,
  /** 通过上/下一条导航链接识别串联。 */
  PREV_NEXT_BLOCK,
}

/** 描述区块中提取到的投稿链接（标签文本 + URL）。 */
private data class BlockSubmissionLink(
    /** 链接的显示文本。 */
    val label: String,
    /** 规范化后的投稿 URL。 */
    val submissionUrl: String,
)

/** 编号区块匹配结果（区块索引、序号、投稿 URL）。 */
private data class NumberedBlockMatch(
    /** 命中的区块索引。 */
    val blockIndex: Int,
    /** 解析出的 part 序号。 */
    val partNumber: Int,
    /** 对应的投稿 URL。 */
    val submissionUrl: String,
)

/** 串联种子加载结果，含已加载投稿及前后分页 Key。 */
private data class SubmissionSeriesSeedResult(
    /** 已加载的投稿列表。 */
    val submissions: List<Submission>,
    /** 加载前一页的请求 Key，无则为 null。 */
    val previousRequestKey: String?,
    /** 加载下一页的请求 Key，无则为 null。 */
    val nextRequestKey: String?,
)

/** 在描述区块中检测 prev/next 导航链接，识别 PREV_NEXT_BLOCK 串联候选。 */
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
    var navigationLabelStartOffset: Int? = null

    links.forEach { link ->
      when (classifyNavigationLink(link.label)) {
        SubmissionSeriesNavigationLinkType.FIRST ->
            if (firstUrl == null) firstUrl = link.submissionUrl

        SubmissionSeriesNavigationLinkType.PREVIOUS -> {
          if (previousUrl == null) previousUrl = link.submissionUrl
          navigationLabelStartOffset =
              chooseEarliestNavigationLabelOffset(
                  current = navigationLabelStartOffset,
                  sourceText = block.sourceText,
                  label = link.label,
              )
        }

        SubmissionSeriesNavigationLinkType.NEXT -> {
          if (nextUrl == null) nextUrl = link.submissionUrl
          navigationLabelStartOffset =
              chooseEarliestNavigationLabelOffset(
                  current = navigationLabelStartOffset,
                  sourceText = block.sourceText,
                  label = link.label,
              )
        }
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
          anchorTextLineEndOffset =
              navigationLabelStartOffset?.let { offset ->
                sourceLineEndOffset(text = block.sourceText, offset = offset)
              },
          rule = SubmissionSeriesRule.PREV_NEXT_BLOCK,
          firstSubmissionUrl = firstUrl,
          previousSubmissionUrl = previousUrl,
          nextSubmissionUrl = nextUrl,
      )
    }
  }
  return null
}

/** 在描述区块中检测编号段落列表，识别 NUMBERED_BLOCKS 串联候选。 */
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

/** 检测单个区块是否符合编号规则，返回匹配结果或 null。 */
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

/** 从 HTML 片段中提取所有 FA 投稿链接（标签文本 + 规范化 URL）。 */
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

private fun chooseEarliestNavigationLabelOffset(
    current: Int?,
    sourceText: String,
    label: String,
): Int? {
  val offset = sourceText.indexOf(label, ignoreCase = true).takeIf { it >= 0 } ?: return current
  return if (current == null || offset < current) offset else current
}

private fun sourceLineEndOffset(text: String, offset: Int): Int {
  val lineEnd = text.indexOf('\n', startIndex = offset)
  return if (lineEnd >= 0) lineEnd else text.length
}

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
    private val repository: SubmissionSeriesSubmissionSource,
    private val throttle: SequentialRequestThrottle,
) {
  private val log = FaLog.withTag("SubmissionSeriesRemoteLoader")

  suspend fun load(url: String): Submission? {
    throttle.awaitReady()
    return when (val state = repository.loadSubmissionDetailByUrl(url)) {
      is PageState.Success ->
          state.data.also { log.d { "投稿串联远程加载 -> 成功(url=${summarizeUrl(url)},sid=${it.id})" } }
      is PageState.AuthRequired,
      PageState.CfChallenge,
      is PageState.MatureBlocked,
      is PageState.Error,
      PageState.Loading ->
          null.also {
            log.w { "投稿串联远程加载 -> 失败(url=${summarizeUrl(url)},state=${state::class.simpleName})" }
          }
    }
  }
}

private fun HtmlTextBlock.toDescriptionBlock(): SubmissionDescriptionBlock =
    SubmissionDescriptionBlock(originalHtml = originalHtml, sourceText = sourceText)

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
