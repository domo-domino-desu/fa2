package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.window.Dialog
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState
import fa2.shared.generated.resources.*
import kotlin.math.max
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
import me.domino.fa2.ui.pages.submission.SubmissionImageOcrTranslationMode
import me.domino.fa2.ui.pages.submission.SubmissionImageOcrUiState
import me.domino.fa2.util.isGifUrl
import me.domino.fa2.util.logging.FaLog
import org.jetbrains.compose.resources.stringResource

private val imageOcrOverlayLog = FaLog.withTag("ImageOcrOverlay")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmissionZoomImageOverlay(
    imageUrl: String,
    ocrState: SubmissionImageOcrUiState,
    onToggleOcr: () -> Unit,
    onTranslateOcr: () -> Unit,
    translationEnabled: Boolean,
    onOpenBlockDialog: (String) -> Unit,
    onDismissBlockDialog: () -> Unit,
    onUpdateDialogDraft: (String) -> Unit,
    onRefreshBlockTranslation: () -> Unit,
    onMergeBlocks: (String, List<NormalizedImagePoint>) -> Unit,
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
              canOpenBlockDialog =
                  translationEnabled &&
                      ocrState.translationMode == SubmissionImageOcrTranslationMode.APPLIED,
              dragEnabled = ocrState.translationMode != SubmissionImageOcrTranslationMode.LOADING,
              onBlockClick = onOpenBlockDialog,
              onMergeBlocks = onMergeBlocks,
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
                imageVector = FaMaterialSymbols.Outlined.DocumentScanner,
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
        if (translationEnabled && ocrState is SubmissionImageOcrUiState.Showing) {
          ExpressiveIconButton(
              onClick = onTranslateOcr,
              enabled = ocrState.translationMode != SubmissionImageOcrTranslationMode.LOADING,
          ) {
            if (ocrState.translationMode == SubmissionImageOcrTranslationMode.LOADING) {
              LoadingIndicator(modifier = Modifier.size(18.dp), color = Color(0xD8D8D8D8))
            } else {
              Icon(
                  imageVector = FaMaterialSymbols.Outlined.Translate,
                  contentDescription = stringResource(Res.string.image_ocr_translate_action),
                  tint =
                      when (ocrState.translationMode) {
                        SubmissionImageOcrTranslationMode.APPLIED ->
                            MaterialTheme.colorScheme.primary
                        SubmissionImageOcrTranslationMode.ERROR -> MaterialTheme.colorScheme.error
                        SubmissionImageOcrTranslationMode.IDLE,
                        SubmissionImageOcrTranslationMode.LOADING -> Color(0xD8D8D8D8)
                      },
              )
            }
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
    canOpenBlockDialog: Boolean,
    dragEnabled: Boolean,
    onBlockClick: (String) -> Unit,
    onMergeBlocks: (String, List<NormalizedImagePoint>) -> Unit,
    modifier: Modifier = Modifier,
) {
  if (contentDisplayRect.width <= 0 || contentDisplayRect.height <= 0 || blocks.isEmpty()) return
  val density = LocalDensity.current
  val viewConfiguration = LocalViewConfiguration.current
  val baseOverlayTextStyle = MaterialTheme.typography.labelSmall
  val overlayTextStyle =
      baseOverlayTextStyle.copy(
          fontSize = baseOverlayTextStyle.fontSize * 0.75f,
          lineHeight =
              if (baseOverlayTextStyle.lineHeight.isUnspecified) {
                baseOverlayTextStyle.lineHeight
              } else {
                baseOverlayTextStyle.lineHeight * 0.75f
              },
      )
  val overlayHorizontalPadding = 2.dp
  val overlayVerticalPadding = 1.dp
  val minimumBlockHeightPx =
      with(density) {
        val lineHeight =
            if (overlayTextStyle.lineHeight.isUnspecified) {
              (overlayTextStyle.fontSize * 1.2f).toPx()
            } else {
              overlayTextStyle.lineHeight.toPx()
            }
        lineHeight + overlayVerticalPadding.toPx() * 2f
      }
  val dragSlopPx = viewConfiguration.touchSlop * 1.35f
  val renderedBlocks =
      remember(blocks, contentDisplayRect, minimumBlockHeightPx) {
        blocks.mapNotNull { it.toRenderedBlock(contentDisplayRect, minimumBlockHeightPx) }
      }
  var dragState by remember(renderedBlocks) { mutableStateOf<ActiveImageOcrDrag?>(null) }
  val mergePreview =
      remember(renderedBlocks, dragState) {
        resolveImageOcrMergePreview(renderedBlocks = renderedBlocks, dragState = dragState)
      }
  val blockRenderOrder =
      remember(renderedBlocks, dragState?.blockId) {
        renderedBlocks.sortedBy { block -> if (block.id == dragState?.blockId) 1 else 0 }
      }
  val isMergeDragging = dragState != null
  val mergeOverlayShape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
  val mergeOverlayBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
  val mergeOverlayBorderColor = MaterialTheme.colorScheme.tertiary

  Box(modifier = modifier.clipToBounds()) {
    blockRenderOrder.forEach { block ->
      key(block.id) {
        val offset =
            IntOffset(
                x = block.visualRect.left.roundToInt(),
                y = block.visualRect.top.roundToInt(),
            )
        val canOpenThisBlock = canOpenBlockDialog && block.hasTranslation
        Surface(
            color =
                if (isMergeDragging) mergeOverlayBackgroundColor
                else Color.White.copy(alpha = 0.96f),
            contentColor = if (isMergeDragging) mergeOverlayBorderColor else Color.Black,
            shape =
                if (isMergeDragging) mergeOverlayShape
                else androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            modifier =
                Modifier.offset { offset }
                    .size(
                        width = with(density) { block.visualRect.width.coerceAtLeast(20f).toDp() },
                        height =
                            with(density) { block.visualRect.height.coerceAtLeast(20f).toDp() },
                    )
                    .then(
                        if (isMergeDragging) {
                          Modifier.border(
                              width = 2.dp,
                              color = mergeOverlayBorderColor,
                              shape = mergeOverlayShape,
                          )
                        } else {
                          Modifier
                        }
                    )
                    .pointerInput(
                        block.id,
                        dragEnabled,
                        canOpenThisBlock,
                        renderedBlocks,
                        contentDisplayRect,
                        dragSlopPx,
                    ) {
                      imageOcrOverlayLog.d {
                        "安装拖拽监听 -> blockId=${block.id}, rect=${block.visualRect.toLogString()}"
                      }
                      awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        val downAbsolutePosition =
                            Offset(
                                x = block.visualRect.left + down.position.x,
                                y = block.visualRect.top + down.position.y,
                            )
                        var trackedAbsolutePosition = downAbsolutePosition
                        var moved = false
                        imageOcrOverlayLog.i {
                          "开始拖拽 OCR 框 -> blockId=${block.id}, 起点=${downAbsolutePosition.toLogString()}"
                        }

                        while (true) {
                          val change = currentPointerChange(pointerId) ?: break
                          if (!change.pressed) {
                            imageOcrOverlayLog.d {
                              "收到抬起事件 -> blockId=${block.id}, 当前=${trackedAbsolutePosition.toLogString()}"
                            }
                            break
                          }

                          val delta = change.position - change.previousPosition
                          if (delta != Offset.Zero) {
                            trackedAbsolutePosition += delta
                            if (
                                dragEnabled &&
                                    (moved ||
                                        (trackedAbsolutePosition - downAbsolutePosition)
                                            .getDistance() > dragSlopPx)
                            ) {
                              moved = true
                              dragState =
                                  ActiveImageOcrDrag(
                                      blockId = block.id,
                                      startPointerPosition = downAbsolutePosition,
                                      pointerPosition = trackedAbsolutePosition,
                                  )
                              change.consume()
                            }
                          }
                        }

                        val currentDrag = dragState
                        dragState = null
                        if (dragEnabled && moved) {
                          val preview =
                              resolveImageOcrMergePreview(
                                  renderedBlocks = renderedBlocks,
                                  dragState = currentDrag,
                              )
                          if (
                              currentDrag != null &&
                                  preview != null &&
                                  preview.selectedBlockIds.size >= 2
                          ) {
                            imageOcrOverlayLog.i {
                              "提交 OCR 框合并 -> draggedBlockId=${currentDrag.blockId}, 命中=${preview.selectedBlockIds.joinToString()}, 选区=${preview.selectionRect.toLogString()}"
                            }
                            onMergeBlocks(
                                currentDrag.blockId,
                                preview.selectionRect.toNormalizedPoints(contentDisplayRect),
                            )
                          } else {
                            imageOcrOverlayLog.i {
                              "结束拖拽但未合并 -> blockId=${block.id}, 最终位置=${trackedAbsolutePosition.toLogString()}, 预览命中=${preview?.selectedBlockIds?.joinToString().orEmpty()}"
                            }
                          }
                        } else if (canOpenThisBlock) {
                          imageOcrOverlayLog.i { "点击 OCR 框打开弹窗 -> blockId=${block.id}" }
                          onBlockClick(block.id)
                        } else {
                          imageOcrOverlayLog.d { "结束手势但未触发动作 -> blockId=${block.id}" }
                        }
                      }
                    },
        ) {
          if (!isMergeDragging) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(
                            horizontal = overlayHorizontalPadding,
                            vertical = overlayVerticalPadding,
                        )
            ) {
              Text(
                  text = block.text,
                  style = overlayTextStyle,
              )
            }
          }
        }
      }
    }
    mergePreview?.let { preview ->
      val offset =
          IntOffset(
              x = preview.previewRect.left.roundToInt(),
              y = preview.previewRect.top.roundToInt(),
          )
      Box(
          modifier =
              Modifier.offset { offset }
                  .size(
                      width = with(density) { preview.previewRect.width.coerceAtLeast(20f).toDp() },
                      height =
                          with(density) { preview.previewRect.height.coerceAtLeast(20f).toDp() },
                  )
                  .clip(mergeOverlayShape)
                  .background(mergeOverlayBackgroundColor)
                  .border(
                      width = 2.dp,
                      color = mergeOverlayBorderColor,
                      shape = mergeOverlayShape,
                  ),
      )
    }
  }
}

private data class RenderedImageOcrBlock(
    val id: String,
    val text: String,
    val hasTranslation: Boolean,
    val rawRect: Rect,
    val visualRect: Rect,
)

private fun SubmissionImageOcrBlockUiState.toRenderedBlock(
    contentDisplayRect: IntRect,
    minimumBlockHeightPx: Float,
): RenderedImageOcrBlock? {
  val mappedPoints = points.map { it.toContainerPoint(contentDisplayRect) }
  if (mappedPoints.isEmpty()) return null
  val minX = mappedPoints.minOf { it.first }
  val maxX = mappedPoints.maxOf { it.first }
  val minY = mappedPoints.minOf { it.second }
  val maxY = mappedPoints.maxOf { it.second }
  if (maxX <= minX || maxY <= minY) return null
  val rawRect = Rect(left = minX, top = minY, right = maxX, bottom = maxY)
  val visualRect =
      expandOverlayRectForMinimumHeight(rawRect, minimumBlockHeightPx, contentDisplayRect)
  return RenderedImageOcrBlock(
      id = id,
      text = displayText,
      hasTranslation = hasTranslation,
      rawRect = rawRect,
      visualRect = visualRect,
  )
}

private fun NormalizedImagePoint.toContainerPoint(contentDisplayRect: IntRect): Pair<Float, Float> =
    Pair(
        contentDisplayRect.left + contentDisplayRect.width * x.coerceIn(0f, 1f),
        contentDisplayRect.top + contentDisplayRect.height * y.coerceIn(0f, 1f),
    )

private data class ActiveImageOcrDrag(
    val blockId: String,
    val startPointerPosition: Offset,
    val pointerPosition: Offset,
)

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.currentPointerChange(
    pointerId: PointerId
): androidx.compose.ui.input.pointer.PointerInputChange? {
  while (true) {
    val event = awaitPointerEvent()
    val matchingChange = event.changes.firstOrNull { change -> change.id == pointerId }
    if (matchingChange != null) return matchingChange
    if (event.changes.none { change -> change.pressed }) {
      imageOcrOverlayLog.d { "拖拽事件流结束 -> pointerId=$pointerId" }
      return null
    }
    imageOcrOverlayLog.d { "拖拽事件流中未找到目标指针 -> pointerId=$pointerId, 当前指针数=${event.changes.size}" }
  }
}

private data class ImageOcrMergePreview(
    val selectedBlockIds: Set<String>,
    val previewRect: Rect,
    val selectionRect: Rect,
)

private fun resolveImageOcrMergePreview(
    renderedBlocks: List<RenderedImageOcrBlock>,
    dragState: ActiveImageOcrDrag?,
): ImageOcrMergePreview? {
  val currentDrag = dragState ?: return null
  val draggedBlock =
      renderedBlocks.firstOrNull { block -> block.id == currentDrag.blockId } ?: return null
  val basePreviewRect =
      draggedBlock.toPreviewRect(
          dragStartPosition = currentDrag.startPointerPosition,
          pointerPosition = currentDrag.pointerPosition,
      )
  var expandedPreviewRect = basePreviewRect
  var selectedBlockIds = setOf(draggedBlock.id)
  var selectionRect = draggedBlock.rawRect

  while (true) {
    val intersectingBlocks =
        renderedBlocks.filter { block ->
          block.id != draggedBlock.id && expandedPreviewRect.overlapArea(block.toVisualRect()) > 0f
        }
    val nextSelectedIds = intersectingBlocks.map { block -> block.id }.toSet() + draggedBlock.id
    val nextPreviewRect =
        intersectingBlocks.fold(basePreviewRect) { currentRect, block ->
          currentRect.union(block.toVisualRect())
        }
    val nextSelectionRect =
        renderedBlocks
            .filter { block -> block.id in nextSelectedIds }
            .map { block -> block.toRawRect() }
            .reduce(Rect::union)
    if (
        nextSelectedIds == selectedBlockIds &&
            nextPreviewRect == expandedPreviewRect &&
            nextSelectionRect == selectionRect
    ) {
      imageOcrOverlayLog.d {
        "解析拖拽预览 -> draggedBlockId=${draggedBlock.id}, 基础选区=${basePreviewRect.toLogString()}, 最终预览=${nextPreviewRect.toLogString()}, 最终提交=${nextSelectionRect.toLogString()}, 命中=${nextSelectedIds.joinToString()}"
      }
      return ImageOcrMergePreview(
          selectedBlockIds = nextSelectedIds,
          previewRect = nextPreviewRect,
          selectionRect = nextSelectionRect,
      )
    }
    selectedBlockIds = nextSelectedIds
    expandedPreviewRect = nextPreviewRect
    selectionRect = nextSelectionRect
  }
}

private fun RenderedImageOcrBlock.toRawRect(offset: Offset = Offset.Zero): Rect =
    rawRect.translate(offset)

private fun RenderedImageOcrBlock.toVisualRect(offset: Offset = Offset.Zero): Rect =
    visualRect.translate(offset)

private fun RenderedImageOcrBlock.toPreviewRect(
    dragStartPosition: Offset,
    pointerPosition: Offset,
): Rect {
  val originalRect = toVisualRect()
  val horizontalDraggedRight = pointerPosition.x >= dragStartPosition.x
  val verticalDraggedDown = pointerPosition.y >= dragStartPosition.y
  return Rect(
      left = if (horizontalDraggedRight) originalRect.left else pointerPosition.x,
      top = if (verticalDraggedDown) originalRect.top else pointerPosition.y,
      right = if (horizontalDraggedRight) pointerPosition.x else originalRect.right,
      bottom = if (verticalDraggedDown) pointerPosition.y else originalRect.bottom,
  )
}

internal fun expandOverlayRectForMinimumHeight(
    rawRect: Rect,
    minimumHeightPx: Float,
    contentDisplayRect: IntRect,
): Rect {
  val targetHeight =
      max(rawRect.height, minimumHeightPx).coerceAtMost(contentDisplayRect.height.toFloat())
  val maxTop =
      (contentDisplayRect.bottom.toFloat() - targetHeight).coerceAtLeast(
          contentDisplayRect.top.toFloat()
      )
  val centeredTop = rawRect.center.y - targetHeight / 2f
  val top = centeredTop.coerceIn(contentDisplayRect.top.toFloat(), maxTop)
  return Rect(left = rawRect.left, top = top, right = rawRect.right, bottom = top + targetHeight)
}

private fun Rect.translate(offset: Offset): Rect =
    Rect(
        left = left + offset.x,
        top = top + offset.y,
        right = right + offset.x,
        bottom = bottom + offset.y,
    )

private fun Rect.overlapArea(other: Rect): Float {
  val overlapWidth = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f)
  val overlapHeight = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
  return overlapWidth * overlapHeight
}

private fun Rect.union(other: Rect): Rect =
    Rect(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

private fun Rect.toNormalizedPoints(contentDisplayRect: IntRect): List<NormalizedImagePoint> {
  if (contentDisplayRect.width <= 0 || contentDisplayRect.height <= 0) return emptyList()
  val containerWidth = contentDisplayRect.width.toFloat()
  val containerHeight = contentDisplayRect.height.toFloat()
  fun normalizeX(value: Float): Float =
      ((value - contentDisplayRect.left.toFloat()) / containerWidth).coerceIn(0f, 1f)
  fun normalizeY(value: Float): Float =
      ((value - contentDisplayRect.top.toFloat()) / containerHeight).coerceIn(0f, 1f)
  return listOf(
      NormalizedImagePoint(normalizeX(left), normalizeY(top)),
      NormalizedImagePoint(normalizeX(right), normalizeY(top)),
      NormalizedImagePoint(normalizeX(right), normalizeY(bottom)),
      NormalizedImagePoint(normalizeX(left), normalizeY(bottom)),
  )
}

private fun Offset.toLogString(): String = "(${x.formatForLog()}, ${y.formatForLog()})"

private fun Rect.toLogString(): String =
    "[${left.formatForLog()}, ${top.formatForLog()}]-[${right.formatForLog()}, ${bottom.formatForLog()}]"

private fun Float.formatForLog(): String = ((this * 10).toInt() / 10f).toString()

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
  val bodyScrollState = rememberScrollState()

  Dialog(onDismissRequest = onDismiss) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        modifier =
            Modifier.fillMaxWidth()
                .widthIn(max = 720.dp)
                .heightIn(max = 640.dp)
                .navigationBarsPadding(),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(bodyScrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
      }
    }
  }
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
