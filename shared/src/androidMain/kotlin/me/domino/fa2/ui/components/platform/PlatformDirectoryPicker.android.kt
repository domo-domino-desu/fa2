package me.domino.fa2.ui.components.platform

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformDirectoryPicker(onDirectoryPicked: (String?) -> Unit): () -> Unit {
  /** 持有最新回调，避免 launcher 捕获旧闭包。 */
  val latestCallback = rememberUpdatedState(onDirectoryPicked)
  /** 读取应用上下文并用于持久化目录权限。 */
  val context = LocalContext.current
  /** 绑定系统目录选择器。 */
  val launcher =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
          latestCallback.value(null)
          return@rememberLauncherForActivityResult
        }
        runCatching {
              val flags =
                  Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
              context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            .onFailure { error -> error.printStackTrace() }
        latestCallback.value(uri.toString())
      }
  /** 对外暴露触发器。 */
  return remember(launcher) { { launcher.launch(null) } }
}
