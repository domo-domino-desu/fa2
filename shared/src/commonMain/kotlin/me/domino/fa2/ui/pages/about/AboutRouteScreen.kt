package me.domino.fa2.ui.pages.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.about_acknowledgements_furaffinity_app
import fa2.shared.generated.resources.about_acknowledgements_transfur_bar
import fa2.shared.generated.resources.about_description
import fa2.shared.generated.resources.about_export_logs
import fa2.shared.generated.resources.about_export_logs_failed
import fa2.shared.generated.resources.about_export_logs_success
import fa2.shared.generated.resources.about_libraries
import fa2.shared.generated.resources.about_license
import fa2.shared.generated.resources.about_project_address
import fa2.shared.generated.resources.about_project_address_url
import fa2.shared.generated.resources.about_thanks
import fa2.shared.generated.resources.about_version
import fa2.shared.generated.resources.about_version_copied
import fa2.shared.generated.resources.close
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.domino.fa2.generated.AboutMetadata
import me.domino.fa2.ui.components.LocalShowToast
import me.domino.fa2.ui.components.accessibilityHeading
import me.domino.fa2.ui.components.accessibleClickableSummary
import me.domino.fa2.ui.components.platform.PlatformTextFileDestination
import me.domino.fa2.ui.components.platform.PlatformTextFileWriteRequest
import me.domino.fa2.ui.components.platform.PlatformTextFileWriteResult
import me.domino.fa2.ui.components.platform.rememberPlatformFileSavePicker
import me.domino.fa2.ui.components.platform.rememberPlatformTextCopier
import me.domino.fa2.ui.components.platform.rememberPlatformTextFileWriter
import me.domino.fa2.ui.host.LocalAppSettings
import me.domino.fa2.ui.icons.FaBrandIcons
import me.domino.fa2.ui.icons.FaContactIcons
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.layouts.AboutRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.util.logging.FaLog
import org.jetbrains.compose.resources.stringResource

private const val aboutProjectUrl = "https://github.com/domo-domino-desu/fa2"
private const val furAffinityAppUrl = "https://github.com/Ceylo/FurAffinityApp"
private val aboutHeaderBackground = Color(0xFF12284F)

/** 关于页面。 */
class AboutRouteScreen : Screen {
  override val key: String = "about-route"

  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val uriHandler = LocalUriHandler.current
    val settings = LocalAppSettings.current
    val showToast = LocalShowToast.current
    val copyTextToClipboard = rememberPlatformTextCopier()
    val writeTextFile = rememberPlatformTextFileWriter()
    val awaitPickedFilePath = rememberAwaitSaveFilePath()
    val coroutineScope = rememberCoroutineScope()
    val versionCopiedText = stringResource(Res.string.about_version_copied)
    val exportLogsFailedText = stringResource(Res.string.about_export_logs_failed)
    val exportLogsSuccessText = stringResource(Res.string.about_export_logs_success)
    var licenseDialogVisible by remember { mutableStateOf(false) }
    var exportingLogs by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
      AboutRouteTopBar(onBack = { navigator.pop() }, onGoHome = { navigator.goBackHome() })

      LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        item { AboutHeaderCard() }
        item {
          AboutActionPanel(
              items =
                  listOf(
                      AboutActionItem(
                          icon = FaContactIcons.GitHub,
                          title = stringResource(Res.string.about_project_address),
                          subtitle = stringResource(Res.string.about_project_address_url),
                          onClick = { uriHandler.openUri(aboutProjectUrl) },
                      ),
                      AboutActionItem(
                          icon = FaMaterialSymbols.Outlined.Code,
                          title = stringResource(Res.string.about_version),
                          subtitle = AboutMetadata.versionName,
                          onClick = {
                            if (copyTextToClipboard(AboutMetadata.versionName)) {
                              showToast(versionCopiedText)
                            }
                          },
                      ),
                      AboutActionItem(
                          icon = FaMaterialSymbols.Outlined.ReceiptLong,
                          title = stringResource(Res.string.about_libraries),
                          onClick = { navigator.push(AboutLibrariesRouteScreen()) },
                      ),
                      AboutActionItem(
                          icon = FaMaterialSymbols.Outlined.Attribution,
                          title = stringResource(Res.string.about_license),
                          onClick = { licenseDialogVisible = true },
                      ),
                      AboutActionItem(
                          icon = FaMaterialSymbols.Outlined.OutputCircle,
                          title = stringResource(Res.string.about_export_logs),
                          onClick = {
                            if (exportingLogs) return@AboutActionItem
                            exportingLogs = true
                            coroutineScope.launch {
                              val logText =
                                  FaLog.exportRuntimeLogText(
                                      appVersionName = AboutMetadata.versionName
                                  )
                              val fileName = buildLogExportFileName()
                              val destination =
                                  settings.downloadSavePath
                                      .trim()
                                      .takeIf { it.isNotBlank() }
                                      ?.let { PlatformTextFileDestination.Directory(it) }
                                      ?: awaitPickedFilePath(fileName)?.let { selectedPath ->
                                        PlatformTextFileDestination.File(selectedPath)
                                      }

                              if (destination == null) {
                                exportingLogs = false
                                return@launch
                              }

                              when (
                                  val result =
                                      writeTextFile(
                                          PlatformTextFileWriteRequest(
                                              destination = destination,
                                              fileName = fileName,
                                              content = logText,
                                          )
                                      )
                              ) {
                                is PlatformTextFileWriteResult.Saved ->
                                    showToast(exportLogsSuccessText.format(result.savedPath))

                                is PlatformTextFileWriteResult.Failure ->
                                    showToast(exportLogsFailedText.format(result.message))
                              }
                              exportingLogs = false
                            }
                          },
                      ),
                  )
          )
        }
        item { ThanksSection(onOpenFurAffinityApp = { uriHandler.openUri(furAffinityAppUrl) }) }
      }
    }

    if (licenseDialogVisible) {
      LicenseDialog(onDismiss = { licenseDialogVisible = false })
    }
  }
}

private data class AboutActionItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun AboutHeaderCard() {
  Column(
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Surface(
        modifier = Modifier.size(84.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = aboutHeaderBackground,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = FaBrandIcons.Logo,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(256.dp),
        )
      }
    }
    Text(
        text = stringResource(Res.string.about_description),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
  }
}

@Composable
private fun AboutActionPanel(items: List<AboutActionItem>) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
  ) {
    Column {
      items.forEachIndexed { index, item ->
        AboutActionRow(
            icon = item.icon,
            title = item.title,
            subtitle = item.subtitle,
            onClick = item.onClick,
        )
        if (index != items.lastIndex) {
          HorizontalDivider(
              modifier = Modifier.padding(start = 52.dp),
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
          )
        }
      }
    }
  }
}

@Composable
private fun AboutActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(onClick = onClick)
              .accessibleClickableSummary(title = title, subtitle = subtitle)
              .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
    )
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Icon(
        imageVector = FaMaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ThanksSection(onOpenFurAffinityApp: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text = stringResource(Res.string.about_thanks),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.accessibilityHeading(),
    )
    Text(
        text = stringResource(Res.string.about_acknowledgements_transfur_bar),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(Res.string.about_acknowledgements_furaffinity_app),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier =
            Modifier.clickable(onClick = onOpenFurAffinityApp)
                .accessibleClickableSummary(
                    title = stringResource(Res.string.about_acknowledgements_furaffinity_app)
                ),
    )
  }
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
  val scrollState = rememberScrollState()
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(text = stringResource(Res.string.about_license)) },
      text = {
        Column(
            modifier = Modifier.height(360.dp).horizontalScroll(rememberScrollState()),
        ) {
          Text(
              text = AboutMetadata.licenseText,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.verticalScroll(scrollState),
          )
        }
      },
      confirmButton = {
        TextButton(onClick = onDismiss) { Text(text = stringResource(Res.string.close)) }
      },
      dismissButton = {},
  )
}

@Composable
private fun rememberAwaitSaveFilePath(): suspend (String) -> String? {
  var pendingHandler by remember { mutableStateOf<((String?) -> Unit)?>(null) }
  val launchPicker =
      rememberPlatformFileSavePicker(mimeType = "text/plain") { selectedPath ->
        pendingHandler?.invoke(selectedPath)
        pendingHandler = null
      }
  return remember(launchPicker) {
    { suggestedFileName ->
      suspendCancellableCoroutine { continuation ->
        pendingHandler = { selectedPath ->
          if (continuation.isActive) {
            continuation.resume(selectedPath)
          }
        }
        launchPicker(suggestedFileName)
        continuation.invokeOnCancellation {
          if (pendingHandler != null) {
            pendingHandler = null
          }
        }
      }
    }
  }
}

private fun buildLogExportFileName(): String {
  val timestamp =
      Clock.System.now()
          .toString()
          .substringBefore('.')
          .removeSuffix("Z")
          .replace(":", "")
          .replace("-", "")
          .replace("T", "-")
  return "fa2-log-$timestamp.txt"
}
