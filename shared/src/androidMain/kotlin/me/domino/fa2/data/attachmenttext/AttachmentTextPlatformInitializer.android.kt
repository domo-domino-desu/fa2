package me.domino.fa2.data.attachmenttext

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import me.domino.fa2.domain.attachmenttext.*
import me.domino.fa2.util.logging.FaLog

private val log = FaLog.withTag("AttachmentTextPlatform")

/** 初始化 Android 侧附件文本平台依赖。 */
fun initializeAttachmentTextPlatform(context: Context) {
  if (PDFBoxResourceLoader.isReady()) return
  PDFBoxResourceLoader.init(context.applicationContext)
  log.i { "初始化附件文本平台 -> PDFBox 资源加载器已就绪" }
}
