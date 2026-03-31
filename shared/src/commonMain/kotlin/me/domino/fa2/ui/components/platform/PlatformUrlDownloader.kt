package me.domino.fa2.ui.components.platform

import androidx.compose.runtime.Composable

/** 下载投稿附件（返回平台处理结果）。 */
@Composable
expect fun rememberPlatformUrlDownloader():
    suspend (PlatformDownloadRequest) -> PlatformDownloadResult
