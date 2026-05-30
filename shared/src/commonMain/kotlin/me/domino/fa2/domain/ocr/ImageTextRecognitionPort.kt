package me.domino.fa2.domain.ocr

/** 归一化坐标系中的图像点。 */
data class NormalizedImagePoint(
    /** 横坐标（0.0～1.0）。 */
    val x: Float,
    /** 纵坐标（0.0～1.0）。 */
    val y: Float,
)

/** OCR 识别出的单个文本块。 */
data class RecognizedTextBlock(
    /** 识别文本内容。 */
    val text: String,
    /** 文本块的顶点坐标列表。 */
    val points: List<NormalizedImagePoint>,
    /** 识别置信度（可选）。 */
    val confidence: Float? = null,
)

/** 图像 OCR 识别结果。 */
data class ImageOcrResult(
    /** 识别出的文本块列表。 */
    val blocks: List<RecognizedTextBlock>,
)

/** 图像文字识别端口接口。 */
interface ImageTextRecognitionPort {
  /** 对给定的图像字节数组执行文字识别并返回结果。 */
  suspend fun recognize(imageBytes: ByteArray): ImageOcrResult
}
