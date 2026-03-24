package me.domino.fa2.ui.components.platform

import androidx.compose.runtime.Composable

/** 复制文本到剪贴板（返回是否复制成功）。 */
@Composable expect fun rememberPlatformTextCopier(): (String) -> Boolean

/** 系统分享文本（返回是否成功触发）。 */
@Composable expect fun rememberPlatformTextSharer(): (String) -> Boolean
