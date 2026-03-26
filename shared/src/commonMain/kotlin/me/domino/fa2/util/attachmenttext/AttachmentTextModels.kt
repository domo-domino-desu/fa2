package me.domino.fa2.util.attachmenttext

/** 附件文本格式。 */
enum class AttachmentTextFormat {
  DOCX,
  ODT,
  RTF,
  PDF,
  MARKDOWN,
  TEXT,
  HTML,
}

/** 单个段落。 */
data class AttachmentTextParagraph(
    /** 段落 HTML。 */
    val html: String,
    /** 来源标签。 */
    val sourceLabel: String? = null,
)

/** 解析结果。 */
data class AttachmentTextDocument(
    /** 附件格式。 */
    val format: AttachmentTextFormat,
    /** 完整 HTML。 */
    val html: String,
    /** 段落列表。 */
    val paragraphs: List<AttachmentTextParagraph>,
)

/** 解析进度。 */
data class AttachmentTextProgress(
    /** 总进度。 */
    val overallFraction: Float,
    /** 当前阶段序号。 */
    val stageIndex: Int,
    /** 总阶段数。 */
    val stageCount: Int,
    /** 当前阶段 ID。 */
    val stageId: String,
    /** 当前阶段标题。 */
    val stageLabel: String,
    /** 当前阶段内进度。 */
    val stageFraction: Float,
    /** 当前动作描述。 */
    val message: String,
    /** 当前处理对象。 */
    val currentItemLabel: String? = null,
)

/** 内联样式。 */
internal data class AttachmentInlineStyle(
    /** 粗体。 */
    val bold: Boolean = false,
    /** 斜体。 */
    val italic: Boolean = false,
    /** 删除线。 */
    val strike: Boolean = false,
    /** 下划线。 */
    val underline: Boolean = false,
)

/** 带样式的文本片段。 */
internal data class StyledTextRun(
    /** 文本内容。 */
    val text: String,
    /** 片段样式。 */
    val style: AttachmentInlineStyle = AttachmentInlineStyle(),
)

/** PDF 行信息。 */
internal data class PdfLine(
    /** 行文本。 */
    val text: String,
    /** 行宽。 */
    val width: Double,
    /** 页序号。 */
    val pageIndex: Int,
    /** 是否页末。 */
    val isEndOfPage: Boolean,
)

/** 进度阶段定义。 */
internal data class AttachmentTextProgressStageSpec(
    /** 阶段 ID。 */
    val id: String,
    /** 阶段标题。 */
    val label: String,
    /** 阶段权重。 */
    val weight: Float,
)
