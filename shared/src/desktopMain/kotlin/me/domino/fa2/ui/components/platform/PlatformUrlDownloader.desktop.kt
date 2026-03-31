package me.domino.fa2.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import fa2.shared.generated.resources.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
  /** 记住平台下载处理函数。 */
  return remember(downloadSource, settingsService) {
    { request -> downloadToConfiguredDirectory(request, downloadSource, settingsService) }
  }
}

/** 将附件保存到已配置目录。 */
private suspend fun downloadToConfiguredDirectory(
    request: PlatformDownloadRequest,
    downloadSource: AttachmentDownloadSource,
    settingsService: AppSettingsService,
): PlatformDownloadResult =
    withContext(Dispatchers.IO) {
      val normalizedUrl = request.downloadUrl.trim()
      if (normalizedUrl.isBlank()) {
        return@withContext PlatformDownloadResult.HandledFailure(
            appString(Res.string.missing_attachment_download_url)
        )
      }

      settingsService.ensureLoaded()
      val settings = settingsService.settings.value
      val savePath = settings.downloadSavePath.trim()
      if (savePath.isBlank()) {
        return@withContext PlatformDownloadResult.HandledFailure(
            appString(Res.string.download_save_path_not_configured)
        )
      }

      val rootDirectory = File(savePath)
      if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
        return@withContext saveFailed(
            appString(Res.string.download_save_error_cannot_create_save_directory)
        )
      }
      if (!rootDirectory.isDirectory) {
        return@withContext saveFailed(appString(Res.string.download_save_error_invalid_path))
      }

      val targetDirectory =
          resolveDownloadRelativeDirectories(settings, request).fold(rootDirectory) {
              current,
              segment ->
            File(current, segment)
          }
      if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
        return@withContext saveFailed(
            appString(Res.string.download_save_error_cannot_create_subdirectory)
        )
      }

      runCatching { syncNoMediaFlag(rootDirectory, settings.downloadAllowMediaIndexing) }
          .getOrElse { error ->
            return@withContext saveFailed(
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
      when (
          val result = downloadSource.fetch(url = normalizedUrl, fileName = provisionalFileName)
      ) {
        is AttachmentDownloadResult.Success -> {
          val finalExtension =
              resolveDownloadFileExtension(
                  fileNameHint = request.downloadFileNameHint,
                  downloadUrl = request.downloadUrl,
                  contentType = result.payload.contentType,
              )
          val finalName = composeFileName(baseName, finalExtension)
          val targetFile = File(targetDirectory, finalName)
          val writeSuccess =
              runCatching {
                    targetFile.outputStream().use { output -> output.write(result.payload.bytes) }
                  }
                  .isSuccess
          if (!writeSuccess) {
            return@withContext saveFailed(
                appString(Res.string.download_save_error_cannot_write_target_file)
            )
          }
          PlatformDownloadResult.Saved
        }

        is AttachmentDownloadResult.Blocked -> saveFailed(result.reason)

        is AttachmentDownloadResult.Challenge ->
            saveFailed(appString(Res.string.cloudflare_challenge_title))

        is AttachmentDownloadResult.Failed -> saveFailed(result.message)
      }
    }

/** 同步根目录 `.nomedia` 标记。 */
private fun syncNoMediaFlag(rootDirectory: File, allowMediaIndexing: Boolean) {
  val marker = File(rootDirectory, ".nomedia")
  if (allowMediaIndexing) {
    if (marker.exists()) {
      marker.delete()
    }
    return
  }
  if (!marker.exists()) {
    marker.createNewFile()
  }
}

/** 构建统一保存失败结果。 */
private fun saveFailed(reason: String): PlatformDownloadResult.HandledFailure =
    PlatformDownloadResult.HandledFailure(appString(Res.string.download_save_failed, reason))
