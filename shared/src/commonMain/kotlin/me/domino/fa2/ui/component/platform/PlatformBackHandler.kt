package me.domino.fa2.ui.component.platform

import androidx.compose.runtime.Composable

/** 平台返回键拦截（无能力平台自动降级为 no-op）。 */
@Composable expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
