package me.domino.fa2.ui.components.platform

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import fa2.shared.generated.resources.*
import me.domino.fa2.data.network.endpoint.AttachmentDownloadResult
import me.domino.fa2.data.network.endpoint.AttachmentDownloadSource
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.appString
import org.koin.compose.koinInject

@Composable
actual fun rememberPlatformUrlDownloader():
    suspend (PlatformDownloadRequest) -> PlatformDownloadResult {
  /** 注入附件下载源。 */
  val downloadSource = koinInject<AttachmentDownloadSource>()
  /** 注入全局设置服务。 */
  val settingsService = koinInject<AppSettingsService>()
  /** 注入 Android 上下文。 */
  val context = LocalContext.current
  /** 记住平台下载处理函数。 */
  return remember(downloadSource, settingsService, context) {
    { request -> downloadToConfiguredDirectory(request, downloadSource, settingsService, context) }
  }
}

/** 将附件保存到已配置目录。 */
private suspend fun downloadToConfiguredDirectory(
    request: PlatformDownloadRequest,
    downloadSource: AttachmentDownloadSource,
    settingsService: AppSettingsService,
    context: Context,
): PlatformDownloadResult {
  val normalizedUrl = request.downloadUrl.trim()
  if (normalizedUrl.isBlank()) {
    return PlatformDownloadResult.HandledFailure(
        appString(Res.string.missing_attachment_download_url)
    )
  }

  settingsService.ensureLoaded()
  val settings = settingsService.settings.value
  val treeUriString = settings.downloadSavePath.trim()
  if (treeUriString.isBlank()) {
    return PlatformDownloadResult.HandledFailure(
        appString(Res.string.download_save_path_not_configured)
    )
  }

  val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull()
  if (treeUri == null || treeUri.scheme?.lowercase() != "content") {
    return saveFailed(appString(Res.string.download_save_error_invalid_path))
  }

  val rootDirectory = DocumentFile.fromTreeUri(context, treeUri)
  if (rootDirectory == null || !rootDirectory.isDirectory) {
    return saveFailed(appString(Res.string.download_save_error_cannot_access_directory))
  }

  val targetDirectory =
      resolveDownloadRelativeDirectories(settings, request).fold(rootDirectory) { current, segment
        ->
        current.findFile(segment)?.takeIf { file -> file.isDirectory }
            ?: current.createDirectory(segment)
            ?: return PlatformDownloadResult.HandledFailure(
                appString(
                    Res.string.download_save_failed,
                    appString(Res.string.download_save_error_cannot_create_subdirectory),
                )
            )
      }

  runCatching { syncNoMediaFlag(rootDirectory, settings.downloadAllowMediaIndexing) }
      .getOrElse { error ->
        return saveFailed(
            error.message ?: appString(Res.string.download_save_error_sync_nomedia_failed)
        )
      }

  val baseName = buildDownloadFileBaseName(settings, request)
  val provisionalExtension =
      resolveDownloadFileExtension(
          fileNameHint = request.downloadFileNameHint,
          downloadUrl = request.downloadUrl,
          contentType = null,
      )
  val provisionalFileName = composeFileName(baseName, provisionalExtension)
  val fetchResult = downloadSource.fetch(url = normalizedUrl, fileName = provisionalFileName)
  when (fetchResult) {
    is AttachmentDownloadResult.Success -> {
      val finalExtension =
          resolveDownloadFileExtension(
              fileNameHint = request.downloadFileNameHint,
              downloadUrl = request.downloadUrl,
              contentType = fetchResult.payload.contentType,
          )
      val finalFileName = composeFileName(baseName, finalExtension)
      val mimeType =
          fetchResult.payload.contentType?.substringBefore(';')?.trim()?.ifBlank { null }
              ?: "application/octet-stream"

      val existing = targetDirectory.findFile(finalFileName)
      if (existing != null) {
        existing.delete()
      }
      val created = targetDirectory.createFile(mimeType, finalFileName)
      if (created == null) {
        return saveFailed(appString(Res.string.download_save_error_cannot_create_target_file))
      }

      val writeSuccess =
          runCatching {
                context.contentResolver.openOutputStream(created.uri, "w")?.use { output ->
                  output.write(fetchResult.payload.bytes)
                } ?: error("Cannot open output stream")
              }
              .isSuccess
      if (!writeSuccess) {
        return saveFailed(appString(Res.string.download_save_error_cannot_write_target_file))
      }
      return PlatformDownloadResult.Saved
    }

    is AttachmentDownloadResult.Blocked -> return saveFailed(fetchResult.reason)

    is AttachmentDownloadResult.Challenge ->
        return saveFailed(appString(Res.string.cloudflare_challenge_title))

    is AttachmentDownloadResult.Failed -> return saveFailed(fetchResult.message)
  }
}

/** 同步根目录 `.nomedia` 标记。 */
private fun syncNoMediaFlag(rootDirectory: DocumentFile, allowMediaIndexing: Boolean) {
  val marker = rootDirectory.findFile(".nomedia")
  if (allowMediaIndexing) {
    marker?.delete()
    return
  }
  if (marker == null) {
    rootDirectory.createFile("application/octet-stream", ".nomedia")
  }
}

/** 构建统一保存失败结果。 */
private fun saveFailed(reason: String): PlatformDownloadResult.HandledFailure =
    PlatformDownloadResult.HandledFailure(appString(Res.string.download_save_failed, reason))
