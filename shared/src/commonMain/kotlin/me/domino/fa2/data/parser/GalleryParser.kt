package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.http.encodeURLParameter
import me.domino.fa2.data.model.GalleryFolder
import me.domino.fa2.data.model.GalleryFolderGroup
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.TagBlockSettings
import me.domino.fa2.util.ensureUserPageAccessible
import me.domino.fa2.util.isBlockedByTagSettings
import me.domino.fa2.util.parseImageTags
import me.domino.fa2.util.parsePositiveFloat
import me.domino.fa2.util.parseSubmissionAvatarUrls
import me.domino.fa2.util.parseSubmissionSid
import me.domino.fa2.util.parseTagBlockSettings
import me.domino.fa2.util.toAbsoluteUrl

/** User 投稿类分页解析器（Gallery/Favorites/Scraps）。 */
class GalleryParser {
  private val folderIdRegex = Regex("""/folder/(\d+)/""")

  private val figureSelectors = listOf("section.gallery-section figure", "section.gallery figure")

  /** 解析投稿分页。 */
  fun parse(html: String, baseUrl: String, defaultAuthor: String): GalleryPage {
    val document = Ksoup.parse(html, baseUrl)
    ensureUserPageAccessible(document)
    val submissions =
        parseSubmissions(
            html = html,
            baseUrl = baseUrl,
            defaultAuthor = defaultAuthor,
            document = document,
        )
    val nextPageUrl = parseNextPageUrl(document = document, baseUrl = baseUrl)
    val currentPageNumber = parseCurrentPageNumber(document = document, baseUrl = baseUrl)
    val folderGroups = parseFolderGroups(document, baseUrl)

    return GalleryPage(
        submissions = submissions,
        nextPageUrl = nextPageUrl,
        currentPageNumber = currentPageNumber,
        folderGroups = folderGroups,
    )
  }

  /** 解析通用投稿列表分页（Browse / Search）。 */
  fun parseListing(
      html: String,
      baseUrl: String,
      defaultAuthor: String = "",
  ): SubmissionListingPage {
    val document = Ksoup.parse(html, baseUrl)
    val submissions =
        parseSubmissions(
            html = html,
            baseUrl = baseUrl,
            defaultAuthor = defaultAuthor,
            document = document,
        )
    val nextPageUrl = parseNextPageUrl(document = document, baseUrl = baseUrl)
    val currentPageNumber = parseCurrentPageNumber(document = document, baseUrl = baseUrl)
    val totalCount = parseListingTotalCount(document)
    return SubmissionListingPage(
        submissions = submissions,
        nextPageUrl = nextPageUrl,
        currentPageNumber = currentPageNumber,
        totalCount = totalCount,
    )
  }

  private fun parseSubmissions(
      html: String,
      baseUrl: String,
      defaultAuthor: String,
      document: com.fleeksoft.ksoup.nodes.Document,
  ): List<SubmissionThumbnail> {
    val tagBlockSettings = parseTagBlockSettings(document)
    val profileAvatarUrl =
        document
            .selectFirst("userpage-nav-avatar img")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> toAbsoluteUrl(baseUrl, raw) }
            .orEmpty()
    val figures =
        figureSelectors
            .asSequence()
            .map { selector -> document.select(selector) }
            .firstOrNull { nodes -> nodes.isNotEmpty() }
            .orEmpty()
    val avatarUrls = parseSubmissionAvatarUrls(html)
    val map = LinkedHashMap<Int, SubmissionThumbnail>()
    figures.forEach { node ->
      val parsed =
          parseFigure(
              node = node,
              defaultAuthor = defaultAuthor,
              avatarUrls = avatarUrls,
              fallbackAuthorAvatarUrl = profileAvatarUrl,
              tagBlockSettings = tagBlockSettings,
          )
      if (parsed != null) {
        map[parsed.id] = parsed
      }
    }
    return map.values.toList()
  }

  private fun parseFigure(
      node: Element,
      defaultAuthor: String,
      avatarUrls: Map<Int, String>,
      fallbackAuthorAvatarUrl: String,
      tagBlockSettings: TagBlockSettings,
  ): SubmissionThumbnail? {
    val rawSubmissionUrl = node.selectFirst("a[href*='/view/']")?.attr("href").orEmpty()
    val submissionUrl =
        toAbsoluteUrl(
            baseUrl = "https://www.furaffinity.net/",
            maybeRelativeUrl = rawSubmissionUrl,
        )
    val id =
        node.attr("id").removePrefix("sid-").toIntOrNull()
            ?: parseSubmissionSid(submissionUrl)
            ?: return null

    val image =
        node.selectFirst("a[href*='/view/'] > img")
            ?: node.selectFirst("a[href*='/view/'] img")
            ?: return null

    val captionLinks = node.select("figcaption p a")
    val title =
        captionLinks.getOrNull(0)?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: image.attr("alt").trim().ifBlank { "Untitled #$id" }

    val dataUserAuthor = node.attr("data-user").removePrefix("u-").trim().ifBlank { "" }
    val author =
        captionLinks.getOrNull(1)?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: dataUserAuthor.ifBlank { defaultAuthor }
    val normalizedDefaultAuthor = defaultAuthor.trim().lowercase()
    val resolvedAvatarUrl =
        avatarUrls[id]?.takeIf { value -> value.isNotBlank() }
            ?: fallbackAuthorAvatarUrl.takeIf {
              it.isNotBlank() && author.trim().lowercase() == normalizedDefaultAuthor
            }

    val width =
        parsePositiveFloat(image.attr("data-width"))
            ?: parsePositiveFloat(image.attr("width"))
            ?: 1f
    val height =
        parsePositiveFloat(image.attr("data-height"))
            ?: parsePositiveFloat(image.attr("height"))
            ?: 1f

    val thumbnailRaw = resolveThumbnailRawUrl(image)
    val thumbnailUrl =
        toAbsoluteUrl(
            baseUrl = "https://www.furaffinity.net/",
            maybeRelativeUrl = thumbnailRaw,
        )
    val resolvedSubmissionUrl = submissionUrl.ifBlank { FaUrls.submission(id) }
    val imageTags = parseImageTags(image)
    val categoryTag = imageTags.sorted().firstOrNull { tag -> tag.startsWith("c_") }.orEmpty()
    val isBlockedByTag =
        isBlockedByTagSettings(imageTags = imageTags, tagBlockSettings = tagBlockSettings)

    return SubmissionThumbnail(
        id = id,
        submissionUrl = resolvedSubmissionUrl,
        title = title,
        author = author,
        authorAvatarUrl = resolvedAvatarUrl.orEmpty(),
        thumbnailUrl = thumbnailUrl,
        thumbnailAspectRatio = width / height,
        categoryTag = categoryTag,
        isBlockedByTag = isBlockedByTag,
    )
  }

  private fun resolveThumbnailRawUrl(image: Element): String {
    val direct =
        listOf(
                "src",
                "data-src",
                "data-preview-src",
                "data-fullview-src",
                "data-lazy-src",
                "data-original",
            )
            .asSequence()
            .map { attribute -> image.attr(attribute).trim() }
            .firstOrNull { value -> value.isNotBlank() }
    if (!direct.isNullOrBlank()) {
      return direct
    }

    return extractSrcsetFirstUrl(image.attr("srcset"))
  }

  private fun extractSrcsetFirstUrl(rawSrcset: String): String =
      rawSrcset.substringBefore(',').substringBefore(' ').trim()

  private fun parseFolderGroups(
      document: com.fleeksoft.ksoup.nodes.Document,
      currentUrl: String,
  ): List<GalleryFolderGroup> {
    val root = document.selectFirst("div.folder-list div.user-folders") ?: return emptyList()
    val groups = mutableListOf<GalleryFolderGroup>()
    var pendingTitle: String? = null

    root.children().forEach { child ->
      when {
        child.tagName() == "div" && child.hasClass("container-item-top") -> {
          pendingTitle = child.text().trim().ifBlank { null }
        }

        (child.tagName() == "div" && child.hasClass("default-folders")) ||
            child.tagName() == "ul" -> {
          val listNode = if (child.tagName() == "div") child.selectFirst("ul") else child
          val folders =
              listNode?.select("li")?.mapNotNull { node -> parseFolder(node, currentUrl) }.orEmpty()
          if (folders.isNotEmpty()) {
            groups += GalleryFolderGroup(title = pendingTitle, folders = folders)
          }
          pendingTitle = null
        }
      }
    }

    return groups
  }

  private fun parseFolder(node: Element, currentUrl: String): GalleryFolder? {
    val linkNode = node.selectFirst("a")
    val title =
        linkNode?.text()?.trim()?.takeIf { text -> text.isNotBlank() }
            ?: node.text().replace("❯❯", "").trim().takeIf { text -> text.isNotBlank() }
            ?: return null

    val folderUrl =
        linkNode
            ?.attr("href")
            ?.trim()
            ?.takeIf { href -> href.isNotBlank() }
            ?.let { href -> toAbsoluteUrl(currentUrl, href) } ?: currentUrl

    val activeByClass =
        node.hasClass("active") ||
            node.hasClass("current") ||
            linkNode?.hasClass("active") == true ||
            linkNode?.hasClass("current") == true ||
            linkNode?.attr("aria-current")?.equals("page", ignoreCase = true) == true
    val activeByFolderId =
        extractFolderId(folderUrl)?.let { folderId -> folderId == extractFolderId(currentUrl) }
            ?: false
    val activeByUrl = normalizeUrlForCompare(folderUrl) == normalizeUrlForCompare(currentUrl)

    return GalleryFolder(
        title = title,
        url = folderUrl,
        isActive = activeByClass || activeByFolderId || activeByUrl,
    )
  }

  private fun extractFolderId(url: String): String? =
      folderIdRegex.find(url)?.groupValues?.getOrNull(1)

  private fun normalizeUrlForCompare(url: String): String =
      url.trim().lowercase().substringBefore('#').substringBefore('?').removeSuffix("/")

  private fun isNextButton(node: Element): Boolean {
    val label = node.text().trim().lowercase()
    val rel = node.attr("rel").trim().lowercase()
    val aria = node.attr("aria-label").trim().lowercase()
    val submitLabel =
        node
            .selectFirst("button, input[type=submit]")
            ?.let { submit -> submit.attr("value").ifBlank { submit.text() }.trim().lowercase() }
            .orEmpty()
    return rel == "next" ||
        aria.startsWith("next") ||
        label.startsWith("next") ||
        submitLabel.startsWith("next")
  }

  private fun parseNextPageUrl(
      document: com.fleeksoft.ksoup.nodes.Document,
      baseUrl: String,
  ): String? {
    val nextNode = document.select("a, form").firstOrNull(::isNextButton) ?: return null
    return if (nextNode.tagName().equals("form", ignoreCase = true)) {
      buildGetFormUrl(formNode = nextNode, baseUrl = baseUrl)
    } else {
      nextNode
          .attr("href")
          .trim()
          .takeIf { value -> value.isNotBlank() }
          ?.let { raw -> toAbsoluteUrl(baseUrl, raw) }
    }
  }

  private fun buildGetFormUrl(formNode: Element, baseUrl: String): String? {
    val method = formNode.attr("method").trim().lowercase()
    if (method.isNotBlank() && method != "get") return null
    val actionRaw = formNode.attr("action").trim()
    val actionUrl =
        if (actionRaw.isBlank()) {
          baseUrl
        } else {
          toAbsoluteUrl(baseUrl, actionRaw)
        }
    val params =
        formNode.select("input[name], select[name], textarea[name]").mapNotNull { input ->
          val name = input.attr("name").trim()
          if (name.isBlank()) return@mapNotNull null

          val type = input.attr("type").trim().lowercase()
          if (type == "submit" || type == "button") return@mapNotNull null
          if ((type == "checkbox" || type == "radio") && !input.hasAttr("checked"))
              return@mapNotNull null

          val value = input.attr("value").trim()
          name to value
        }

    if (params.isEmpty()) return actionUrl
    val query =
        params.joinToString("&") { (name, value) ->
          "${name.encodeURLParameter()}=${value.encodeURLParameter()}"
        }
    val separator = if ('?' in actionUrl) '&' else '?'
    return "$actionUrl$separator$query"
  }

  private fun parseCurrentPageNumber(
      document: com.fleeksoft.ksoup.nodes.Document,
      baseUrl: String,
  ): Int? {
    val textCandidates =
        listOfNotNull(
            document.selectFirst(".page-number strong")?.text(),
            document.selectFirst(".pagination strong.highlight")?.text(),
            document.selectFirst(".navigation-page-name")?.text(),
        )
    textCandidates.forEach { text ->
      pageNumberRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it
      }
    }
    return pageQueryRegex.find(baseUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
  }

  private fun parseListingTotalCount(document: com.fleeksoft.ksoup.nodes.Document): Int? {
    val queryStatsText = document.selectFirst("#query-stats")?.text().orEmpty()
    if (queryStatsText.isBlank()) return null
    return searchTotalCountRegex
        .find(queryStatsText)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(",", "")
        ?.toIntOrNull()
  }

  companion object {
    private val pageNumberRegex = Regex("""Page\s*#?\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val pageQueryRegex = Regex("""(?:\?|&)page=(\d+)""", RegexOption.IGNORE_CASE)
    private val searchTotalCountRegex = Regex("""of\s+([\d,]+)\)""", RegexOption.IGNORE_CASE)
  }
}
