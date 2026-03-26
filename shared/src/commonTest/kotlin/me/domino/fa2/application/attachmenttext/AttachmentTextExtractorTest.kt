package me.domino.fa2.application.attachmenttext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.attachmenttext.UnsupportedAttachmentTextFormatException
import me.domino.fa2.domain.attachmenttext.AttachmentTextFormat
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.domain.attachmenttext.PdfLine
import me.domino.fa2.domain.attachmenttext.mergeLinesIntoParagraphs
import me.domino.fa2.fake.TestFixtures

/** 附件文本解析测试。 */
class AttachmentTextExtractorTest {
  /** 支持性判断。 */
  @Test
  fun detectsSupportedFormats() {
    assertTrue(AttachmentTextExtractor.isSupported("sample.docx"))
    assertTrue(AttachmentTextExtractor.isSupported("sample.odt"))
    assertTrue(AttachmentTextExtractor.isSupported("sample.rtf"))
    assertTrue(AttachmentTextExtractor.isSupported("sample.pdf"))
    assertTrue(AttachmentTextExtractor.isSupported("sample.md"))
    assertTrue(AttachmentTextExtractor.isSupported("sample.txt"))
    assertTrue(AttachmentTextExtractor.isSupported("sample.htm"))
    assertTrue(AttachmentTextExtractor.isSupported("sample.html"))
    assertFalse(AttachmentTextExtractor.isSupported("sample.doc"))
    assertFalse(AttachmentTextExtractor.isSupported("sample.zip"))
    assertFalse(AttachmentTextExtractor.isSupported("sample.bin"))
    assertFalse(AttachmentTextExtractor.isSupported("sample"))
  }

  /** 不支持格式统一失败。 */
  @Test
  fun failsForUnsupportedFormats() = runTest {
    assertFailsWith<UnsupportedAttachmentTextFormatException> {
      AttachmentTextExtractor.parse("sample.bin", byteArrayOf(1, 2, 3))
    }
  }

  /** 解析带样式 DOCX。 */
  @Test
  fun parsesStyledDocx() = runTest {
    val document =
        AttachmentTextExtractor.parse(
            fileName = "attachmenttext-styled.docx",
            bytes = TestFixtures.readBytes("attachmenttext-styled.docx"),
        )

    assertEquals(AttachmentTextFormat.DOCX, document.format)
    assertEquals(2, document.paragraphs.size)
    assertTrue(document.html.contains("<b>Bold</b>"))
    assertTrue(document.html.contains("<i>Italic</i>"))
    assertTrue(document.html.contains("<s>Strike</s>"))
    assertTrue(document.html.contains("Second paragraph"))
  }

  /** 解析带样式 ODT。 */
  @Test
  fun parsesStyledOdt() = runTest {
    val document =
        AttachmentTextExtractor.parse(
            fileName = "attachmenttext-styled.odt",
            bytes = TestFixtures.readBytes("attachmenttext-styled.odt"),
        )

    assertEquals(AttachmentTextFormat.ODT, document.format)
    assertEquals(2, document.paragraphs.size)
    assertTrue(document.html.contains("<b>Bold</b>"))
    assertTrue(document.html.contains("<i>Italic</i>"))
    assertTrue(document.html.contains("<s>Strike</s>"))
    assertTrue(document.html.contains("Second paragraph"))
  }

  /** 解析 RTF 样式与进度。 */
  @Test
  fun parsesRtfStylesAndReportsProgress() = runTest {
    val progresses = mutableListOf<AttachmentTextProgress>()
    val bytes =
        """{\rtf1\ansi Plain \b Bold\b0  \i Italic\i0  \strike Strike\strike0\par Second paragraph}"""
            .encodeToByteArray()

    val document =
        AttachmentTextExtractor.parse(
            fileName = "sample.rtf",
            bytes = bytes,
            onProgress = progresses::add,
        )

    assertEquals(AttachmentTextFormat.RTF, document.format)
    assertEquals(2, document.paragraphs.size)
    assertTrue(document.html.contains("<b>Bold</b>"))
    assertTrue(document.html.contains("<i>Italic</i>"))
    assertTrue(document.html.contains("<s>Strike</s>"))
    assertProgressCompleted(progresses)
    assertTrue(progresses.any { progress -> progress.stageId == "interpret_groups" })
  }

  /** 忽略 RTF 元数据目标块。 */
  @Test
  fun ignoresRtfLatentStylesMetadata() = runTest {
    val bytes =
        """
        {\rtf1\ansi{\*\latentstyles{\lsdqformat1 Normal;\lsdqformat1 heading 1;}}\pard Actual body\par}
        """
            .trimIndent()
            .encodeToByteArray()

    val document = AttachmentTextExtractor.parse(fileName = "sample.rtf", bytes = bytes)

    assertTrue(document.html.contains("Actual body"))
    assertFalse(document.html.contains("heading 1"))
    assertFalse(document.html.contains("Normal;"))
  }

  /** 忽略非 starred 的 RTF destination，并保留下划线正文。 */
  @Test
  fun ignoresDirectRtfDestinationsAndPreservesUnderline() = runTest {
    val bytes =
        """
        {\rtf1\ansi
        {\fonttbl{\f0 Arial;}}
        {\stylesheet{\s0 Normal;}}
        {\header Header text\par}
        {\footer Footer text\par}
        \pard\ul Title\ulnone\par
        Body text\par}
        """
            .trimIndent()
            .encodeToByteArray()

    val document = AttachmentTextExtractor.parse(fileName = "sample.rtf", bytes = bytes)

    assertTrue(document.html.contains("<u>Title</u>"))
    assertTrue(document.html.contains("Body text"))
    assertFalse(document.html.contains("Arial"))
    assertFalse(document.html.contains("Normal;"))
    assertFalse(document.html.contains("Header text"))
    assertFalse(document.html.contains("Footer text"))
  }

  /** 解析 RTF Unicode 引号。 */
  @Test
  fun parsesRtfUnicodeQuotes() = runTest {
    val bytes = """{\rtf1\ansi\uc0 \u8220 quoted\u8221\par}""".encodeToByteArray()

    val document = AttachmentTextExtractor.parse(fileName = "sample.rtf", bytes = bytes)

    assertTrue(document.html.contains("quoted"))
    assertTrue(document.html.contains("“"))
    assertTrue(document.html.contains("”"))
  }

  /** 解析外部真实附件。 */
  @Test
  fun parsesExternalFixtures() = runTest {
    val fileNames =
        listOf(
            "attachmenttext-external.docx",
            "attachmenttext-external.odt",
            "attachmenttext-external.rtf",
            "attachmenttext-external.pdf",
        )

    fileNames.forEach { fileName ->
      val document =
          AttachmentTextExtractor.parse(
              fileName = fileName,
              bytes = TestFixtures.readBytes(fileName),
          )
      assertTrue(document.paragraphs.isNotEmpty(), fileName)
      assertTrue(document.html.contains("<p>"), fileName)
    }
  }

  /** 解析多页 PDF 并上报页进度。 */
  @Test
  fun parsesMultiPagePdfAndReportsPageProgress() = runTest {
    val progresses = mutableListOf<AttachmentTextProgress>()

    val document =
        AttachmentTextExtractor.parse(
            fileName = "attachmenttext-multipage.pdf",
            bytes = TestFixtures.readBytes("attachmenttext-multipage.pdf"),
            onProgress = progresses::add,
        )

    assertEquals(AttachmentTextFormat.PDF, document.format)
    assertTrue(document.paragraphs.isNotEmpty())
    assertProgressCompleted(progresses)
    assertTrue(progresses.any { progress -> progress.stageId == "extract_pages" })
    assertTrue(progresses.any { progress -> progress.currentItemLabel?.contains("第 1 页") == true })
    assertTrue(progresses.any { progress -> progress.currentItemLabel?.contains("第 4 页") == true })
  }

  /** 解析 TXT 并按段落拆分。 */
  @Test
  fun parsesTxtParagraphsAndEscapesHtml() = runTest {
    val document =
        AttachmentTextExtractor.parse(
            fileName = "sample.txt",
            bytes = "line1\nline2\n\n<script>alert(1)</script>".encodeToByteArray(),
        )

    assertEquals(AttachmentTextFormat.TEXT, document.format)
    assertEquals(2, document.paragraphs.size)
    assertTrue(document.html.contains("line1<br/>line2"))
    assertTrue(document.html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
  }

  /** 解析 Markdown 极简语法。 */
  @Test
  fun parsesMarkdownInlineSyntaxAndDropsImages() = runTest {
    val document =
        AttachmentTextExtractor.parse(
            fileName = "sample.md",
            bytes =
                """
                # Heading

                **Bold** *Italic* ~~Strike~~ [Link](https://example.com) ![Ignored](https://example.com/a.png)
                | A | B |
                """
                    .trimIndent()
                    .encodeToByteArray(),
        )

    assertEquals(AttachmentTextFormat.MARKDOWN, document.format)
    assertTrue(document.html.contains("# Heading"))
    assertTrue(document.html.contains("<b>Bold</b>"))
    assertTrue(document.html.contains("<i>Italic</i>"))
    assertTrue(document.html.contains("<s>Strike</s>"))
    assertTrue(document.html.contains("""<a href="https://example.com">Link</a>"""))
    assertFalse(document.html.contains("Ignored"))
    assertTrue(document.html.contains("| A | B |"))
  }

  /** 解析 HTML 并清理危险节点。 */
  @Test
  fun parsesHtmlBodyAndRemovesDangerousNodes() = runTest {
    val document =
        AttachmentTextExtractor.parse(
            fileName = "sample.html",
            bytes =
                """
                <html>
                  <head>
                    <title>ignored</title>
                    <script>alert(1)</script>
                  </head>
                  <body>
                    <p>Hello <b>world</b></p>
                  </body>
                </html>
                """
                    .trimIndent()
                    .encodeToByteArray(),
        )

    assertEquals(AttachmentTextFormat.HTML, document.format)
    assertTrue(document.html.contains("<p>Hello <b>world</b></p>"))
    assertFalse(document.html.contains("alert(1)"))
    assertFalse(document.html.contains("<title>"))
  }

  /** PDF 行合段。 */
  @Test
  fun mergesPdfLinesIntoParagraphsWithHyphenAndPunctuation() {
    val paragraphs =
        mergeLinesIntoParagraphs(
            listOf(
                PdfLine(
                    text = "This para-",
                    width = 120.0,
                    pageIndex = 0,
                    isEndOfPage = false,
                ),
                PdfLine(
                    text = "graph keeps going",
                    width = 130.0,
                    pageIndex = 0,
                    isEndOfPage = false,
                ),
                PdfLine(
                    text = "and ends here.",
                    width = 90.0,
                    pageIndex = 0,
                    isEndOfPage = false,
                ),
                PdfLine(
                    text = "New paragraph",
                    width = 80.0,
                    pageIndex = 0,
                    isEndOfPage = true,
                ),
            )
        )

    assertEquals(2, paragraphs.size)
    assertEquals("This paragraph keeps going and ends here.", paragraphs[0])
    assertEquals("New paragraph", paragraphs[1])
  }
}

/** 断言进度完成。 */
private fun assertProgressCompleted(progresses: List<AttachmentTextProgress>) {
  assertTrue(progresses.isNotEmpty())
  assertTrue(
      progresses.zipWithNext().all { (left, right) ->
        left.overallFraction <= right.overallFraction
      }
  )
  assertEquals(1f, progresses.last().overallFraction)
  assertTrue(progresses.all { progress -> progress.stageLabel.isNotBlank() })
  assertTrue(progresses.all { progress -> progress.message.isNotBlank() })
}
