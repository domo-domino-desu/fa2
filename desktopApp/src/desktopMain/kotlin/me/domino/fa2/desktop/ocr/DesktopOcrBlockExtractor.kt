package me.domino.fa2.desktop.ocr

import me.domino.fa2.domain.ocr.RecognizedTextBlock

internal fun interface DesktopOcrBlockExtractor {
  suspend fun extract(imageBytes: ByteArray): List<RecognizedTextBlock>
}
