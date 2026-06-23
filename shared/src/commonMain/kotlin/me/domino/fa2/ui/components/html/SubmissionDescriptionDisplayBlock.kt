package me.domino.fa2.ui.components.html

/** 翻译后的描述区块展示数据。 */
data class SubmissionDescriptionDisplayBlock(
    /** 原始 HTML 内容。 */
    val originalHtml: String,
    /** 换行折叠模式下的纯文本内容，非折叠模式为 null。 */
    val originalText: String? = null,
    /** 翻译结果文本，未翻译时为 null。 */
    val translated: String?,
    /** 翻译状态。 */
    val status: SubmissionDescriptionTranslationStatus,
)

/** 描述区块的翻译状态枚举。 */
enum class SubmissionDescriptionTranslationStatus {
  /** 初始未触发状态。 */
  IDLE,
  /** 等待翻译中。 */
  PENDING,
  /** 翻译成功。 */
  SUCCESS,
  /** 翻译结果为空。 */
  EMPTY,
  /** 翻译失败。 */
  FAILURE,
}
