package me.domino.fa2.ui.components.platform

import androidx.compose.runtime.Composable

/** 下载 URL（返回是否由平台处理完成）。 */
@Composable expect fun rememberPlatformUrlDownloader(): suspend (String) -> Boolean
