package me.domino.fa2.ui.components.platform

import androidx.compose.runtime.Composable

sealed interface PlatformTextFileDestination {
  data class Directory(
      val path: String,
      val relativeDirectories: List<String> = emptyList(),
  ) : PlatformTextFileDestination

  data class File(val path: String) : PlatformTextFileDestination
}

data class PlatformTextFileWriteRequest(
    val destination: PlatformTextFileDestination,
    val fileName: String,
    val content: String,
)

sealed interface PlatformTextFileWriteResult {
  data class Saved(val savedPath: String) : PlatformTextFileWriteResult

  data class Failure(val message: String) : PlatformTextFileWriteResult
}

/** 将文本写入到平台文件系统。 */
@Composable
expect fun rememberPlatformTextFileWriter():
    suspend (PlatformTextFileWriteRequest) -> PlatformTextFileWriteResult
