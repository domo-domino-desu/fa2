package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState
import fa2.shared.generated.resources.*
import kotlin.math.roundToInt
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.ui.components.CenterCircularWavyImageLoadingProgress
import me.domino.fa2.ui.components.ExpressiveIconButton
import me.domino.fa2.ui.components.ImageLoadLifecycleState
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.ui.components.rememberImageLoadProgressState
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.pages.submission.SubmissionImageOcrBlockUiState
import me.domino.fa2.ui.pages.submission.SubmissionImageOcrDialogUiState
import me.domino.fa2.ui.pages.submission.SubmissionImageOcrUiState
import me.domino.fa2.util.isGifUrl
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmissionZoomImageOverlay(
    imageUrl: String,
    ocrState: SubmissionImageOcrUiState,
    onToggleOcr: () -> Unit,
    onOpenBlockDialog: (String) -> Unit,
    onDismissBlockDialog: () -> Unit,
    onUpdateDialogDraft: (String) -> Unit,
    onRefreshBlockTranslation: () -> Unit,
    onDismiss: () -> Unit,
) {
  Box(
      modifier =
          Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f))
  ) {
    val normalizedUrl =
        imageUrl.trim().let { url -> if (url.startsWith("//")) "https:$url" else url }
    val isGif = isGifUrl(normalizedUrl)
    if (isGif) {
      NetworkImage(
          url = normalizedUrl,
          modifier = Modifier.fillMaxSize().padding(12.dp).clickable { onDismiss() },
          contentScale = ContentScale.Fit,
          showLoadingPlaceholder = false,
      )
    } else {
      var loadLifecycleState by
          remember(normalizedUrl) { mutableStateOf(ImageLoadLifecycleState.Idle) }
      val progressState =
          rememberImageLoadProgressState(
              progressKey = normalizedUrl,
              lifecycleState = loadLifecycleState,
          )
      val zoomState = rememberCoilZoomState()
      Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        CoilZoomAsyncImage(
            model = normalizedUrl,
            contentDescription = stringResource(Res.string.original_image),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            zoomState = zoomState,
            onLoading = { _ -> loadLifecycleState = ImageLoadLifecycleState.Loading },
            onSuccess = { _ -> loadLifecycleState = ImageLoadLifecycleState.Success },
            onError = { _ -> loadLifecycleState = ImageLoadLifecycleState.Error },
            onTap = { onDismiss() },
        )
        if (ocrState is SubmissionImageOcrUiState.Showing) {
          SubmissionImageOcrOverlay(
              blocks = ocrState.blocks,
              contentDisplayRect = zoomState.zoomable.contentDisplayRect,
              onBlockClick = onOpenBlockDialog,
              modifier = Modifier.fillMaxSize(),
          )
        }
        CenterCircularWavyImageLoadingProgress(
            progressState = progressState,
            modifier = Modifier.align(Alignment.Center),
        )
      }
    }
    Row(
        modifier =
            Modifier.align(Alignment.TopEnd).padding(12.dp).focusProperties { canFocus = false },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (!isGif) {
        ExpressiveIconButton(
            onClick = onToggleOcr,
            enabled = ocrState !is SubmissionImageOcrUiState.Loading,
        ) {
          if (ocrState is SubmissionImageOcrUiState.Loading) {
            LoadingIndicator(modifier = Modifier.size(18.dp), color = Color(0xD8D8D8D8))
          } else {
            Icon(
                imageVector = FaMaterialSymbols.Outlined.Translate,
                contentDescription = stringResource(Res.string.image_ocr_action),
                tint =
                    when (ocrState) {
                      is SubmissionImageOcrUiState.Showing -> MaterialTheme.colorScheme.primary
                      is SubmissionImageOcrUiState.Error -> MaterialTheme.colorScheme.error
                      SubmissionImageOcrUiState.Idle,
                      SubmissionImageOcrUiState.Loading -> Color(0xD8D8D8D8)
                    },
            )
          }
        }
      }
      ExpressiveIconButton(onClick = onDismiss) {
        Icon(
            imageVector = FaMaterialSymbols.Filled.Close,
            contentDescription = stringResource(Res.string.close_preview),
            tint = Color(0xD8D8D8D8),
        )
      }
    }
    val dialogState = (ocrState as? SubmissionImageOcrUiState.Showing)?.dialog
    if (ocrState is SubmissionImageOcrUiState.Showing && dialogState != null) {
      SubmissionImageOcrEditDialog(
          blocks = ocrState.blocks,
          dialogState = dialogState,
          onDismiss = onDismissBlockDialog,
          onDraftChange = onUpdateDialogDraft,
          onRefresh = onRefreshBlockTranslation,
      )
    }
  }
}

@Composable
private fun SubmissionImageOcrOverlay(
    blocks: List<SubmissionImageOcrBlockUiState>,
    contentDisplayRect: IntRect,
    onBlockClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  if (contentDisplayRect.width <= 0 || contentDisplayRect.height <= 0 || blocks.isEmpty()) return
  val density = LocalDensity.current
  val renderedBlocks =
      remember(blocks, contentDisplayRect) {
        blocks.mapNotNull { it.toRenderedBlock(contentDisplayRect) }
      }

  Box(modifier = modifier.clipToBounds()) {
    renderedBlocks.forEach { block ->
      val offset =
          IntOffset(
              x = block.left.roundToInt(),
              y = block.top.roundToInt(),
          )
      Surface(
          color = Color.White.copy(alpha = 0.96f),
          contentColor = Color.Black,
          shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
          modifier =
              Modifier.offset { offset }
                  .clickable { onBlockClick(block.id) }
                  .size(
                      width = with(density) { block.width.coerceAtLeast(20f).toDp() },
                      height = with(density) { block.height.coerceAtLeast(20f).toDp() },
                  ),
      ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
          Text(
              text = block.text,
              style = MaterialTheme.typography.labelSmall,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

private data class RenderedImageOcrBlock(
    val id: String,
    val text: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private fun SubmissionImageOcrBlockUiState.toRenderedBlock(
    contentDisplayRect: IntRect
): RenderedImageOcrBlock? {
  val mappedPoints = points.map { it.toContainerPoint(contentDisplayRect) }
  if (mappedPoints.isEmpty()) return null
  val minX = mappedPoints.minOf { it.first }
  val maxX = mappedPoints.maxOf { it.first }
  val minY = mappedPoints.minOf { it.second }
  val maxY = mappedPoints.maxOf { it.second }
  if (maxX <= minX || maxY <= minY) return null
  return RenderedImageOcrBlock(
      id = id,
      text = displayText,
      left = minX,
      top = minY,
      width = maxX - minX,
      height = maxY - minY,
  )
}

private fun NormalizedImagePoint.toContainerPoint(contentDisplayRect: IntRect): Pair<Float, Float> =
    Pair(
        contentDisplayRect.left + contentDisplayRect.width * x.coerceIn(0f, 1f),
        contentDisplayRect.top + contentDisplayRect.height * y.coerceIn(0f, 1f),
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubmissionImageOcrEditDialog(
    blocks: List<SubmissionImageOcrBlockUiState>,
    dialogState: SubmissionImageOcrDialogUiState,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
  val targetBlock = blocks.firstOrNull { it.id == dialogState.blockId } ?: return
  val normalizedDraft = dialogState.draftOriginalText.trim()
  val hasRefreshAction =
      normalizedDraft.isNotBlank() && normalizedDraft != targetBlock.originalText.trim()

  AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
              text = stringResource(Res.string.image_ocr_edit_dialog_title),
              style = MaterialTheme.typography.titleMedium,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (hasRefreshAction) {
              ExpressiveIconButton(
                  onClick = onRefresh,
                  enabled = !dialogState.refreshing,
              ) {
                if (dialogState.refreshing) {
                  LoadingIndicator(
                      modifier = Modifier.size(18.dp),
                      color = MaterialTheme.colorScheme.primary,
                  )
                } else {
                  Icon(
                      imageVector = FaMaterialSymbols.Outlined.Refresh,
                      contentDescription = stringResource(Res.string.image_ocr_refresh_translation),
                  )
                }
              }
            }
            ExpressiveIconButton(onClick = onDismiss) {
              Icon(
                  imageVector = FaMaterialSymbols.Filled.Close,
                  contentDescription = stringResource(Res.string.close),
              )
            }
          }
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
              value = dialogState.draftOriginalText,
              onValueChange = onDraftChange,
              label = { Text(stringResource(Res.string.image_ocr_original_text)) },
              modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
              minLines = 4,
          )
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.image_ocr_translated_text),
                style = MaterialTheme.typography.labelLarge,
            )
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
              Text(
                  text =
                      dialogState.translatedText
                          ?: targetBlock.translatedText
                          ?: targetBlock.displayText,
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  style = MaterialTheme.typography.bodyMedium,
              )
            }
            dialogState.errorMessage?.let { message ->
              Text(
                  text = message,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {},
  )
}

@Composable
internal fun SubmissionAuthorRow(
    authorDisplayName: String,
    author: String,
    authorAvatarUrl: String,
    timestamp: String,
    onOpenAuthor: (String) -> Unit,
) {
  val normalizedAuthor = author.trim()
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp)
              .then(
                  if (normalizedAuthor.isNotBlank()) {
                    Modifier.clickable { onOpenAuthor(normalizedAuthor) }
                  } else {
                    Modifier
                  }
              ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
    ) {
      if (authorAvatarUrl.isNotBlank()) {
        NetworkImage(
            url = authorAvatarUrl,
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
            showLoadingPlaceholder = false,
        )
      } else {
        Text(
            text = authorDisplayName.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = authorDisplayName,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
      )
      Text(
          text = timestamp,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
