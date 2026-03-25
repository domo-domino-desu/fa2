package me.domino.fa2.ui.components.platform

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformTextCopier(): (String) -> Boolean {
  val context = LocalContext.current
  return remember(context) {
    { text ->
      runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("fa2-link", text))
          }
          .isSuccess
    }
  }
}

@Composable
actual fun rememberPlatformTextSharer(): (String) -> Boolean {
  val context = LocalContext.current
  return remember(context) {
    { text ->
      runCatching {
            val chooserIntent =
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "分享链接",
                )
            if (context !is Activity) {
              chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
          }
          .isSuccess
    }
  }
}

@Composable actual fun rememberPlatformShareActionUsesCopyIcon(): Boolean = false
