package me.domino.fa2.data.translation

import io.ktor.client.HttpClient
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.domain.translation.TranslationPort
import me.domino.fa2.domain.translation.TranslationRequest

/** 基于 Ktor 的翻译端口实现，按 TranslationProvider 分发到对应客户端。 */
class KtorTranslationPort
internal constructor(
    /** Google 翻译客户端。 */
    private val googleClient: TranslationProviderClient,
    /** Microsoft 翻译客户端。 */
    private val microsoftClient: TranslationProviderClient,
    /** OpenAI 兼容翻译客户端。 */
    private val openAiClient: TranslationProviderClient,
) : TranslationPort {
  /** 使用单个 Ktor HttpClient 构建所有翻译客户端的便捷构造函数。 */
  constructor(
      client: HttpClient
  ) : this(
      googleClient = GoogleTranslationClient(KtorTranslationHttpTransport(client)),
      microsoftClient = MicrosoftTranslationClient(KtorTranslationHttpTransport(client)),
      openAiClient = OpenAiCompatibleTranslationClient(KtorTranslationHttpTransport(client)),
  )

  /** 按请求中的 provider 字段选择对应客户端执行翻译。 */
  override suspend fun translate(request: TranslationRequest): String {
    val normalized = request.normalized()
    return when (normalized.provider) {
      TranslationProvider.GOOGLE -> googleClient.translate(normalized)
      TranslationProvider.MICROSOFT -> microsoftClient.translate(normalized)
      TranslationProvider.OPENAI_COMPATIBLE -> openAiClient.translate(normalized)
    }
  }
}
