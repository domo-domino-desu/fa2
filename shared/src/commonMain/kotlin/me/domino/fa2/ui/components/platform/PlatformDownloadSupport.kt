package me.domino.fa2.ui.components.platform

import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.DownloadFileNameMode
import me.domino.fa2.data.settings.DownloadSubfolderMode
import me.domino.fa2.util.cleanupTemplateRenderedText
import me.domino.fa2.util.renderBraceTemplate

/** 平台下载请求。 */
data class PlatformDownloadRequest(
    /** 下载地址。 */
    val downloadUrl: String,
    /** 投稿 ID。 */
    val submissionId: Int,
    /** 投稿标题。 */
    val title: String,
    /** 用户名。 */
    val username: String,
    /** 投稿分类（原始值）。 */
    val category: String,
    /** 投稿分级（原始值）。 */
    val rating: String,
    /** 投稿类型（原始值）。 */
    val type: String,
    /** 投稿物种（原始值）。 */
    val species: String,
    /** 页面解析出的原始下载文件名。 */
    val downloadFileNameHint: String? = null,
)

/** 平台下载处理结果。 */
sealed interface PlatformDownloadResult {
  /** 已由平台处理并保存成功。 */
  data object Saved : PlatformDownloadResult

  /** 已由平台处理但失败（不应回退到外部浏览器）。 */
  data class HandledFailure(val message: String) : PlatformDownloadResult

  /** 平台未处理（可回退到外部浏览器）。 */
  data object NotHandled : PlatformDownloadResult
}

/** 生成下载相对目录。 */
internal fun resolveDownloadRelativeDirectories(
    settings: AppSettings,
    request: PlatformDownloadRequest,
): List<String> =
    when (settings.downloadSubfolderMode) {
      DownloadSubfolderMode.FLAT -> emptyList()
      DownloadSubfolderMode.BY_USERNAME ->
          listOf(sanitizePathSegment(request.username).ifBlank { defaultUsernameDirectory })
    }

/** 生成下载文件名（不含扩展名）。 */
internal fun buildDownloadFileBaseName(
    settings: AppSettings,
    request: PlatformDownloadRequest,
): String {
  val template =
      when (settings.downloadFileNameMode) {
        DownloadFileNameMode.ID_TITLE -> "{submission_id}-{title}"
        DownloadFileNameMode.USERNAME_ID -> "{username}-{submission_id}"
        DownloadFileNameMode.USERNAME_ID_TITLE -> "{username}-{submission_id}-{title}"
        DownloadFileNameMode.CUSTOM -> settings.downloadCustomFileNameTemplate
      }
  val rendered =
      renderBraceTemplate(
          template = template,
          values =
              mapOf(
                  "username" to request.username.trim(),
                  "title" to request.title.trim(),
                  "submission_id" to request.submissionId.toString(),
                  "category" to request.category.trim(),
                  "rating" to request.rating.trim(),
                  "type" to request.type.trim(),
                  "species" to request.species.trim(),
              ),
      )
  val normalized = normalizeTemplateResult(rendered)
  val sanitized = sanitizePathSegment(normalized)
  return sanitized.ifBlank { "submission-${request.submissionId}" }
}

/** 推导下载扩展名。 */
internal fun resolveDownloadFileExtension(
    fileNameHint: String?,
    downloadUrl: String,
    contentType: String?,
): String {
  return extractExtensionFromName(fileNameHint)
      ?: extractExtensionFromUrl(downloadUrl)
      ?: extractExtensionFromContentType(contentType)
      ?: defaultDownloadExtension
}

/** 组装最终文件名。 */
internal fun composeFileName(baseName: String, extension: String): String {
  val safeBase = sanitizePathSegment(baseName).ifBlank { defaultFileBaseName }
  val safeExtension =
      extension.trim().trimStart('.').lowercase().ifBlank { defaultDownloadExtension }
  return "$safeBase.$safeExtension"
}

/** 清理模板渲染后产生的冗余连接符。 */
internal fun normalizeTemplateResult(raw: String): String {
  return cleanupTemplateRenderedText(raw)
}

/** 清理路径段中的非法字符。 */
internal fun sanitizePathSegment(raw: String): String =
    raw.trim()
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '.')

/** 从文件名中提取扩展名。 */
internal fun extractExtensionFromName(fileName: String?): String? {
  val normalized = fileName?.trim().orEmpty()
  if (normalized.isBlank()) return null
  val base = normalized.substringAfterLast('/').substringAfterLast('\\').substringBefore('?')
  val extension = base.substringAfterLast('.', missingDelimiterValue = "").trim().lowercase()
  return extension.takeIf { value -> value.isNotBlank() && value.all(Char::isLetterOrDigit) }
}

/** 从 URL 中提取扩展名。 */
internal fun extractExtensionFromUrl(url: String): String? {
  val normalized = url.trim()
  if (normalized.isBlank()) return null
  val pathPart = normalized.substringBefore('#').substringBefore('?')
  extractExtensionFromName(pathPart.substringAfterLast('/'))?.let {
    return it
  }

  val query = normalized.substringAfter('?', missingDelimiterValue = "")
  return query.split('&').firstNotNullOfOrNull { pair ->
    val key = pair.substringBefore('=', missingDelimiterValue = "").trim().lowercase()
    val value = pair.substringAfter('=', missingDelimiterValue = "").trim()
    when (key) {
      "filename",
      "file",
      "name",
      "download" -> extractExtensionFromName(value)
      else -> null
    }
  }
}

/** 从 Content-Type 推导扩展名。 */
internal fun extractExtensionFromContentType(contentType: String?): String? {
  val normalized = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
  if (normalized.isBlank() || !normalized.contains('/')) return null
  val subtype = normalized.substringAfter('/').substringBefore('+').trim()
  if (subtype.isBlank()) return null
  return when (subtype) {
    "jpeg",
    "pjpeg" -> "jpg"
    "svg+xml" -> "svg"
    "quicktime" -> "mov"
    "octet-stream",
    "x-msdownload",
    "x-www-form-urlencoded" -> null
    else -> subtype.takeIf { value -> value.all(Char::isLetterOrDigit) }
  }
}

private const val defaultUsernameDirectory: String = "unknown-user"
private const val defaultFileBaseName: String = "download"
private const val defaultDownloadExtension: String = "bin"
