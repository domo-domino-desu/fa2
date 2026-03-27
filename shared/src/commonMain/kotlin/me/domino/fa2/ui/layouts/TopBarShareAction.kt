package me.domino.fa2.ui.layouts

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.components.ExpressiveIconButton
import me.domino.fa2.ui.components.LocalShowToast
import me.domino.fa2.ui.components.platform.rememberPlatformShareActionUsesCopyIcon
import me.domino.fa2.ui.components.platform.rememberPlatformTextCopier
import me.domino.fa2.ui.components.platform.rememberPlatformTextSharer
import me.domino.fa2.ui.icons.FaMaterialSymbols
import org.jetbrains.compose.resources.stringResource

/** 顶栏分享动作（优先系统分享，失败回退复制链接）。 */
@Composable
fun TopBarShareAction(url: String) {
  val shareText = rememberPlatformTextSharer()
  val copyTextToClipboard = rememberPlatformTextCopier()
  val useCopyIcon = rememberPlatformShareActionUsesCopyIcon()
  val showToast = LocalShowToast.current
  val normalizedUrl = url.trim()
  val linkCopiedText = stringResource(Res.string.link_copied)
  val copyLinkText = stringResource(Res.string.copy_link)
  val shareLinkText = stringResource(Res.string.share_link)
  ExpressiveIconButton(
      onClick = {
        if (normalizedUrl.isBlank()) return@ExpressiveIconButton
        if (shareText(normalizedUrl)) return@ExpressiveIconButton
        if (copyTextToClipboard(normalizedUrl)) {
          showToast(linkCopiedText)
        }
      },
      enabled = normalizedUrl.isNotBlank(),
  ) {
    Icon(
        imageVector =
            if (useCopyIcon) FaMaterialSymbols.Outlined.ContentCopy
            else FaMaterialSymbols.Outlined.Share,
        contentDescription = if (useCopyIcon) copyLinkText else shareLinkText,
    )
  }
}
