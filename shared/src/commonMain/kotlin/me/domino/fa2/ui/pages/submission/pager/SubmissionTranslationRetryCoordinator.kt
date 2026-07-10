package me.domino.fa2.ui.pages.submission.pager

import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.translation_provider_google
import fa2.shared.generated.resources.translation_provider_microsoft
import fa2.shared.generated.resources.translation_provider_openai_compatible
import fa2.shared.generated.resources.translation_provider_switched
import fa2.shared.generated.resources.translation_switch_provider_and_retry
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.ui.components.feedback.AppFeedbackRequest
import me.domino.fa2.ui.i18n.appString
import me.domino.fa2.utils.concurrency.runSuspendCatching
import org.jetbrains.compose.resources.StringResource

/** Owns provider failover and retry feedback shared by text translation and image OCR. */
internal class SubmissionTranslationRetryCoordinator(
    private val settingsService: AppSettingsService?,
    private val feedbackSink: (AppFeedbackRequest) -> Unit,
) {
  fun currentProvider(): TranslationProvider =
      settingsService?.settings?.value?.translationProvider
          ?: AppSettings.defaultTranslationProvider

  fun emitToast(message: String) {
    feedbackSink(AppFeedbackRequest(message = message))
  }

  suspend fun emitRetryFeedback(message: String, retry: suspend () -> Boolean) {
    val service = settingsService
    if (service == null) {
      emitToast(message)
      return
    }
    val nextProvider = currentProvider().nextRetryProvider()
    feedbackSink(
        AppFeedbackRequest(
            message = message,
            actionLabel = switchProviderAndRetryLabel(nextProvider),
            onAction = {
              service.ensureLoaded()
              service.updateTranslationProvider(nextProvider)
              if (!retry()) emitToast(translationProviderSwitchedMessage(nextProvider))
            },
        )
    )
  }

  private suspend fun providerLabel(provider: TranslationProvider): String =
      when (provider) {
        TranslationProvider.GOOGLE ->
            safeAppStringOrFallback(Res.string.translation_provider_google) { "Google Translate" }
        TranslationProvider.MICROSOFT ->
            safeAppStringOrFallback(Res.string.translation_provider_microsoft) {
              "Microsoft Translator"
            }
        TranslationProvider.OPENAI_COMPATIBLE ->
            safeAppStringOrFallback(Res.string.translation_provider_openai_compatible) {
              "OpenAI Compatible"
            }
      }

  private suspend fun switchProviderAndRetryLabel(provider: TranslationProvider): String {
    val providerLabel = providerLabel(provider)
    return safeAppStringOrFallback(
        Res.string.translation_switch_provider_and_retry,
        providerLabel,
    ) {
      "Switch to $providerLabel and retry"
    }
  }

  private suspend fun translationProviderSwitchedMessage(provider: TranslationProvider): String {
    val providerLabel = providerLabel(provider)
    return safeAppStringOrFallback(Res.string.translation_provider_switched, providerLabel) {
      "Switched translation provider to $providerLabel"
    }
  }
}

private fun TranslationProvider.nextRetryProvider(): TranslationProvider =
    when (this) {
      TranslationProvider.GOOGLE -> TranslationProvider.MICROSOFT
      TranslationProvider.MICROSOFT,
      TranslationProvider.OPENAI_COMPATIBLE -> TranslationProvider.GOOGLE
    }

internal suspend fun safeAppStringOrFallback(
    resource: StringResource,
    vararg formatArgs: Any,
    fallback: () -> String,
): String = runSuspendCatching { appString(resource, *formatArgs) }.getOrElse { fallback() }
