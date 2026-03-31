package me.domino.fa2.ui.components.platform

import androidx.compose.runtime.Composable

/** 触发平台保存文件选择器。返回值接收建议文件名。 */
@Composable
expect fun rememberPlatformFileSavePicker(
    mimeType: String,
    onFilePicked: (String?) -> Unit,
): (String) -> Unit
