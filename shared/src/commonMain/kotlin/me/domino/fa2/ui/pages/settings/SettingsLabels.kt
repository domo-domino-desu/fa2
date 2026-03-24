package me.domino.fa2.ui.pages.settings

import me.domino.fa2.data.settings.ThemeMode
import me.domino.fa2.data.settings.TranslationProvider

internal fun translationProviderLabel(provider: TranslationProvider): String =
    when (provider) {
      TranslationProvider.GOOGLE -> "谷歌翻译"
      TranslationProvider.MICROSOFT -> "微软翻译"
      TranslationProvider.OPENAI_COMPATIBLE -> "OpenAI 兼容 API"
    }

internal fun themeModeLabel(themeMode: ThemeMode): String =
    when (themeMode) {
      ThemeMode.SYSTEM -> "跟随系统"
      ThemeMode.LIGHT -> "亮色主题"
      ThemeMode.DARK -> "暗色主题"
    }
