package me.domino.fa2.ui.layouts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import me.domino.fa2.ui.components.LocalShowToast
import me.domino.fa2.ui.components.platform.rememberPlatformTextCopier
import me.domino.fa2.ui.components.platform.rememberPlatformTextSharer

/** 顶栏分享动作（优先系统分享，失败回退复制链接）。 */
@Composable
fun TopBarShareAction(url: String) {
  val shareText = rememberPlatformTextSharer()
  val copyTextToClipboard = rememberPlatformTextCopier()
  val showToast = LocalShowToast.current
  val normalizedUrl = url.trim()
  IconButton(
    onClick = {
      if (normalizedUrl.isBlank()) return@IconButton
      if (shareText(normalizedUrl)) return@IconButton
      if (copyTextToClipboard(normalizedUrl)) {
        showToast("链接已复制")
      }
    },
    enabled = normalizedUrl.isNotBlank(),
  ) {
    Icon(imageVector = Icons.Outlined.Share, contentDescription = "分享链接")
  }
}
