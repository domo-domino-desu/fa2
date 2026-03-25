package me.domino.fa2.util.attachmenttext

/** 附件解析进度聚合器。 */
class AttachmentTextProgressReporter
internal constructor(
    /** 阶段定义。 */
    private val stages: List<AttachmentTextProgressStageSpec>,
    /** 回调。 */
    private val onProgress: (AttachmentTextProgress) -> Unit,
) {
  /** 总权重。 */
  private val totalWeight: Float = stages.sumOf { stage -> stage.weight.toDouble() }.toFloat()

  /** 阶段索引。 */
  private val stageIndexById: Map<String, Int> =
      stages.mapIndexed { index, stage -> stage.id to index }.toMap()

  /** 已发出的最大总进度。 */
  private var lastOverallFraction: Float = 0f

  /** 发出初始进度。 */
  fun start(message: String, currentItemLabel: String? = null) {
    val firstStage = stages.firstOrNull() ?: return
    report(
        stageId = firstStage.id,
        stageFraction = 0f,
        message = message,
        currentItemLabel = currentItemLabel,
    )
  }

  /** 上报阶段进度。 */
  fun report(
      stageId: String,
      stageFraction: Float,
      message: String,
      currentItemLabel: String? = null,
  ) {
    val index = checkNotNull(stageIndexById[stageId]) { "未知的附件解析阶段：$stageId" }
    val stage = stages[index]
    val normalizedStageFraction = stageFraction.coerceIn(0f, 1f)
    val completedWeight =
        stages.take(index).sumOf { completed -> completed.weight.toDouble() }.toFloat()
    val rawOverall =
        if (totalWeight <= 0f) {
          normalizedStageFraction
        } else {
          (completedWeight + stage.weight * normalizedStageFraction) / totalWeight
        }
    val overallFraction = rawOverall.coerceAtLeast(lastOverallFraction).coerceIn(0f, 1f)
    lastOverallFraction = overallFraction
    onProgress(
        AttachmentTextProgress(
            overallFraction = overallFraction,
            stageIndex = index + 1,
            stageCount = stages.size,
            stageId = stage.id,
            stageLabel = stage.label,
            stageFraction = normalizedStageFraction,
            message = message,
            currentItemLabel = currentItemLabel?.trim()?.ifBlank { null },
        )
    )
  }

  /** 发出完成进度。 */
  fun complete(message: String = "解析完成", currentItemLabel: String? = null) {
    val lastStage = stages.lastOrNull() ?: return
    report(
        stageId = lastStage.id,
        stageFraction = 1f,
        message = message,
        currentItemLabel = currentItemLabel,
    )
  }

  internal companion object {
    /** 按格式创建进度聚合器。 */
    fun create(
        format: AttachmentTextFormat,
        onProgress: (AttachmentTextProgress) -> Unit,
    ): AttachmentTextProgressReporter {
      val stages =
          when (format) {
            AttachmentTextFormat.DOCX,
            AttachmentTextFormat.ODT ->
                listOf(
                    AttachmentTextProgressStageSpec("detect_format", "识别格式", 0.05f),
                    AttachmentTextProgressStageSpec("open_archive", "打开压缩包", 0.10f),
                    AttachmentTextProgressStageSpec("load_document_xml", "读取文档 XML", 0.15f),
                    AttachmentTextProgressStageSpec("walk_blocks", "提取段落", 0.50f),
                    AttachmentTextProgressStageSpec("build_html", "构建 HTML", 0.20f),
                )

            AttachmentTextFormat.RTF ->
                listOf(
                    AttachmentTextProgressStageSpec("decode_bytes", "解码字节", 0.10f),
                    AttachmentTextProgressStageSpec("tokenize", "切分标记", 0.30f),
                    AttachmentTextProgressStageSpec("interpret_groups", "解释语法组", 0.40f),
                    AttachmentTextProgressStageSpec("normalize_paragraphs", "整理段落", 0.10f),
                    AttachmentTextProgressStageSpec("build_html", "构建 HTML", 0.10f),
                )

            AttachmentTextFormat.PDF ->
                listOf(
                    AttachmentTextProgressStageSpec("open_document", "打开 PDF", 0.10f),
                    AttachmentTextProgressStageSpec("extract_pages", "提取页面文本", 0.50f),
                    AttachmentTextProgressStageSpec("merge_paragraphs", "合并段落", 0.25f),
                    AttachmentTextProgressStageSpec("build_html", "构建 HTML", 0.15f),
                )

            AttachmentTextFormat.MARKDOWN ->
                listOf(
                    AttachmentTextProgressStageSpec("decode_bytes", "解码字节", 0.15f),
                    AttachmentTextProgressStageSpec("split_paragraphs", "整理段落", 0.25f),
                    AttachmentTextProgressStageSpec(
                        "interpret_inline_markdown",
                        "解释行内 Markdown",
                        0.40f,
                    ),
                    AttachmentTextProgressStageSpec("build_html", "构建 HTML", 0.20f),
                )

            AttachmentTextFormat.TEXT ->
                listOf(
                    AttachmentTextProgressStageSpec("decode_bytes", "解码字节", 0.25f),
                    AttachmentTextProgressStageSpec("split_paragraphs", "整理段落", 0.40f),
                    AttachmentTextProgressStageSpec("build_html", "构建 HTML", 0.35f),
                )

            AttachmentTextFormat.HTML ->
                listOf(
                    AttachmentTextProgressStageSpec("decode_bytes", "解码字节", 0.15f),
                    AttachmentTextProgressStageSpec("parse_html", "解析 HTML", 0.35f),
                    AttachmentTextProgressStageSpec("sanitize_html", "清理内容", 0.30f),
                    AttachmentTextProgressStageSpec("build_html", "整理输出", 0.20f),
                )
          }
      return AttachmentTextProgressReporter(stages = stages, onProgress = onProgress)
    }
  }
}
