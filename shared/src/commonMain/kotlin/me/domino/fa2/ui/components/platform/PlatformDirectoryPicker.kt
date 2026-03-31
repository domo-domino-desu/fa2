package me.domino.fa2.ui.components.platform

import androidx.compose.runtime.Composable

/** 记住平台目录选择器触发函数。 */
@Composable
expect fun rememberPlatformDirectoryPicker(onDirectoryPicked: (String?) -> Unit): () -> Unit
