package me.domino.fa2.desktop.ocr

import me.domino.fa2.domain.ocr.ImageOcrResult
import me.domino.fa2.domain.ocr.ImageTextRecognitionPort

internal class RapidImageTextRecognitionPort(
    private val extractor: DesktopOcrBlockExtractor,
) : ImageTextRecognitionPort {
  override suspend fun recognize(imageBytes: ByteArray): ImageOcrResult =
      ImageOcrResult(blocks = extractor.extract(imageBytes))
}
