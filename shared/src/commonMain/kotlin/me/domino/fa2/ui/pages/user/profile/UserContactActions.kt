package me.domino.fa2.ui.pages.user.profile

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.copied_to_clipboard
import me.domino.fa2.data.model.UserContact
import me.domino.fa2.ui.components.LocalShowToast
import me.domino.fa2.ui.components.platform.rememberPlatformTextCopier
import me.domino.fa2.ui.icons.FaContactIcons
import me.domino.fa2.util.logging.FaLog
import org.jetbrains.compose.resources.stringResource

private val log = FaLog.withTag("UserContactActions")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserMetadataAndContactsRow(
    userTitle: String,
    registeredAtText: String,
    contacts: List<UserContact>,
) {
  val summary =
      listOf(userTitle, registeredAtText).filter { it.isNotBlank() }.joinToString(" · ").trim()
  if (summary.isBlank() && contacts.isEmpty()) return
  val (iconContacts, textContacts) = contacts.partition { resolveUserContactIcon(it.label) != null }

  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (summary.isNotBlank()) {
      Text(
          text = summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    iconContacts.forEach { contact -> UserContactIconAction(contact = contact) }
    textContacts.forEachIndexed { index, contact ->
      if (summary.isNotBlank() || iconContacts.isNotEmpty() || index > 0) {
        UserContactSeparator()
      }
      UserContactTextAction(contact = contact)
    }
  }
}

@Composable
private fun UserContactIconAction(contact: UserContact) {
  val uriHandler = LocalUriHandler.current
  val copyTextToClipboard = rememberPlatformTextCopier()
  val showToast = LocalShowToast.current
  val copiedText = stringResource(Res.string.copied_to_clipboard)
  val icon = resolveUserContactIcon(contact.label) ?: return

  Icon(
      imageVector = icon,
      contentDescription = displayUserContactLabel(contact.label),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier =
          Modifier.size(14.dp)
              .offset(y = 1.dp)
              .combinedClickable(
                  onClick = {
                    activateUserContact(
                        contact = contact,
                        uriHandler = uriHandler,
                        copyTextToClipboard = copyTextToClipboard,
                        showToast = showToast,
                        copiedText = copiedText,
                    )
                  },
                  onLongClick = {
                    copyUserContact(
                        contact = contact,
                        copyTextToClipboard = copyTextToClipboard,
                        showToast = showToast,
                        copiedText = copiedText,
                        reason = "长按",
                    )
                  },
              ),
  )
}

@Composable
private fun UserContactTextAction(contact: UserContact) {
  val uriHandler = LocalUriHandler.current
  val copyTextToClipboard = rememberPlatformTextCopier()
  val showToast = LocalShowToast.current
  val copiedText = stringResource(Res.string.copied_to_clipboard)

  Text(
      text = buildUserContactText(contact),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontFamily = FontFamily.Monospace,
      textDecoration = TextDecoration.Underline,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
      modifier =
          Modifier.widthIn(max = 260.dp)
              .combinedClickable(
                  onClick = {
                    activateUserContact(
                        contact = contact,
                        uriHandler = uriHandler,
                        copyTextToClipboard = copyTextToClipboard,
                        showToast = showToast,
                        copiedText = copiedText,
                    )
                  },
                  onLongClick = {
                    copyUserContact(
                        contact = contact,
                        copyTextToClipboard = copyTextToClipboard,
                        showToast = showToast,
                        copiedText = copiedText,
                        reason = "长按",
                    )
                  },
              ),
  )
}

@Composable
private fun UserContactSeparator() {
  Text(
      text = "\u00b7",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

private fun activateUserContact(
    contact: UserContact,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    copyTextToClipboard: (String) -> Boolean,
    showToast: (String) -> Unit,
    copiedText: String,
) {
  val url = contact.url.trim()
  val value = contact.value.trim()
  log.i { "用户联系方式点击 -> label=${contact.label},url=$url,value=$value" }

  if (url.isNotBlank()) {
    val openResult =
        runCatching { uriHandler.openUri(url) }
            .onSuccess { log.i { "用户联系方式点击 -> 打开链接成功(label=${contact.label},url=$url)" } }
            .onFailure { error ->
              log.e(error) { "用户联系方式点击 -> 打开链接失败(label=${contact.label},url=$url,value=$value)" }
            }
    if (openResult.isSuccess) return
  }

  copyUserContact(
      contact = contact,
      copyTextToClipboard = copyTextToClipboard,
      showToast = showToast,
      copiedText = copiedText,
      reason = "点击回退",
  )
}

private fun buildUserContactText(contact: UserContact): String {
  return displayUserContactLabel(contact.label)
}

private fun copyUserContact(
    contact: UserContact,
    copyTextToClipboard: (String) -> Boolean,
    showToast: (String) -> Unit,
    copiedText: String,
    reason: String,
) {
  val textToCopy = contact.value.trim().ifBlank { contact.url.trim() }
  if (textToCopy.isBlank()) {
    log.w { "用户联系方式$reason -> 无可复制文本(label=${contact.label})" }
    return
  }

  if (copyTextToClipboard(textToCopy)) {
    log.i { "用户联系方式$reason -> 复制成功(label=${contact.label},text=$textToCopy)" }
    showToast(copiedText)
  } else {
    log.w { "用户联系方式$reason -> 复制失败(label=${contact.label},text=$textToCopy)" }
  }
}

internal fun resolveUserContactIcon(label: String): ImageVector? =
    when (normalizeUserContactLabel(label)) {
      "website" -> FaContactIcons.Website
      "youtube" -> FaContactIcons.Youtube
      "twitter" -> FaContactIcons.Twitter
      "bluesky" -> FaContactIcons.Bluesky
      "twitch" -> FaContactIcons.Twitch
      "facebook" -> FaContactIcons.Facebook
      "instagram" -> FaContactIcons.Instagram
      "mastodon" -> FaContactIcons.Mastodon
      "tiktok" -> FaContactIcons.Tiktok
      "reddit" -> FaContactIcons.Reddit
      "etsy" -> FaContactIcons.Etsy
      "patreon" -> FaContactIcons.Patreon
      "kofi" -> FaContactIcons.KoFi
      "picarto" -> FaContactIcons.Picarto
      "deviantart" -> FaContactIcons.DeviantArt
      "tumblr" -> FaContactIcons.Tumblr
      "telegram" -> FaContactIcons.Telegram
      "discord" -> FaContactIcons.Discord
      "email" -> FaContactIcons.Email
      "weasyl" -> FaContactIcons.Weasyl
      "furrynetwork" -> FaContactIcons.FurryNetwork
      "pixiv" -> FaContactIcons.Pixiv
      "ao3" -> FaContactIcons.Ao3
      "archiveofourown" -> FaContactIcons.Ao3
      "wattpad" -> FaContactIcons.Wattpad
      "steam" -> FaContactIcons.Steam
      "xboxlive" -> FaContactIcons.Xbox
      "xbox" -> FaContactIcons.Xbox
      "playstationnetwork" -> FaContactIcons.PlayStation
      "playstation" -> FaContactIcons.PlayStation
      "3dsfriendcode" -> FaContactIcons.Nintendo3ds
      "switchfriendcode" -> FaContactIcons.NintendoSwitch
      "wiiufriendcode" -> FaContactIcons.WiiU
      "battlenet" -> FaContactIcons.BattleNet
      else -> null
    }

internal fun displayUserContactLabel(label: String): String =
    when (normalizeUserContactLabel(label)) {
      "website" -> "Website"
      "youtube" -> "YouTube"
      "twitter" -> "Twitter"
      "bluesky" -> "Bluesky"
      "twitch" -> "Twitch"
      "facebook" -> "Facebook"
      "instagram" -> "Instagram"
      "mastodon" -> "Mastodon"
      "tiktok" -> "TikTok"
      "reddit" -> "Reddit"
      "etsy" -> "Etsy"
      "patreon" -> "Patreon"
      "kofi" -> "Ko-fi"
      "picarto" -> "Picarto"
      "deviantart" -> "DeviantArt"
      "tumblr" -> "Tumblr"
      "telegram" -> "Telegram"
      "discord" -> "Discord"
      "email" -> "Email"
      "weasyl" -> "Weasyl"
      "furrynetwork" -> "Furry Network"
      "pixiv" -> "Pixiv"
      "ao3" -> "AO3"
      "wattpad" -> "Wattpad"
      "steam" -> "Steam"
      "xboxlive" -> "Xbox Live"
      "xbox" -> "Xbox"
      "playstationnetwork" -> "PlayStation Network"
      "playstation" -> "PlayStation"
      "3dsfriendcode" -> "Nintendo 3DS"
      "switchfriendcode" -> "Nintendo Switch"
      "wiiufriendcode" -> "Wii U"
      "battlenet" -> "Battle.net"
      else -> label.trim().ifBlank { "Link" }
    }

private fun normalizeUserContactLabel(label: String): String =
    buildString(label.length) {
          label.forEach { ch ->
            if (ch.isLetterOrDigit()) {
              append(ch.lowercaseChar())
            }
          }
        }
        .replace("archiveofourown", "ao3")
