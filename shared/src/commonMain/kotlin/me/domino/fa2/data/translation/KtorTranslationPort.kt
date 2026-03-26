package me.domino.fa2.data.translation

import io.ktor.client.HttpClient
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.domain.translation.TranslationPort
import me.domino.fa2.domain.translation.TranslationRequest

/** 基于 Ktor 的翻译端口实现。 */
class KtorTranslationPort
internal constructor(
    private val googleClient: TranslationProviderClient,
    private val microsoftClient: TranslationProviderClient,
    private val openAiClient: TranslationProviderClient,
) : TranslationPort {
  constructor(
      client: HttpClient
  ) : this(
      googleClient = GoogleTranslationClient(KtorTranslationHttpTransport(client)),
      microsoftClient = MicrosoftTranslationClient(KtorTranslationHttpTransport(client)),
      openAiClient = OpenAiCompatibleTranslationClient(KtorTranslationHttpTransport(client)),
  )

  override suspend fun translate(request: TranslationRequest): String {
    val normalized = request.normalized()
    return when (normalized.provider) {
      TranslationProvider.GOOGLE -> googleClient.translate(normalized)
      TranslationProvider.MICROSOFT -> microsoftClient.translate(normalized)
      TranslationProvider.OPENAI_COMPATIBLE -> openAiClient.translate(normalized)
    }
  }
}
