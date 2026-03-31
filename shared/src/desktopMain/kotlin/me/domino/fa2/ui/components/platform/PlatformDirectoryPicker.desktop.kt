package me.domino.fa2.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import fa2.shared.generated.resources.*
import java.awt.EventQueue
import javax.swing.JFileChooser
import me.domino.fa2.i18n.appString

@Composable
actual fun rememberPlatformDirectoryPicker(onDirectoryPicked: (String?) -> Unit): () -> Unit {
  /** 持有最新回调，避免触发器捕获旧闭包。 */
  val latestCallback = rememberUpdatedState(onDirectoryPicked)
  /** 触发目录选择。 */
  return remember {
    {
      val selectedPath =
          runCatching {
                runOnAwtEventThread {
                  JFileChooser().let { chooser ->
                    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    chooser.isMultiSelectionEnabled = false
                    chooser.isAcceptAllFileFilterUsed = false
                    chooser.dialogTitle = appString(Res.string.choose_save_path)
                    val result = chooser.showOpenDialog(null)
                    if (result == JFileChooser.APPROVE_OPTION) {
                      chooser.selectedFile?.absolutePath
                    } else {
                      null
                    }
                  }
                }
              }
              .getOrNull()
      latestCallback.value(selectedPath)
    }
  }
}

/** 在 AWT 事件线程上执行任务。 */
private fun <T> runOnAwtEventThread(block: () -> T): T {
  if (EventQueue.isDispatchThread()) return block()
  var result: Result<T>? = null
  EventQueue.invokeAndWait { result = runCatching(block) }
  return checkNotNull(result).getOrThrow()
}
