package me.domino.fa2.ui.components.submission

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import kotlinx.coroutines.flow.distinctUntilChanged
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.BlockedSubmissionWaterfallMode
import me.domino.fa2.ui.components.ExpressiveButton
import me.domino.fa2.ui.components.ExpressiveTextButton
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.ui.components.ThumbnailImage
import me.domino.fa2.ui.host.LocalTaxonomyCatalog
import me.domino.fa2.ui.host.LocalTaxonomyRepository
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.pages.submission.SubmissionPaginationKind
import org.jetbrains.compose.resources.stringResource

/** 瀑布流卡片最小宽度。 */
private const val defaultWaterfallCardMinWidthDp = 220

/** 瀑布流网格内边距。 */
private val waterfallPadding = PaddingValues(12.dp)

/** 投稿卡片最小宽高比。 */
private const val minCardAspectRatio = 0.45f

/** 投稿卡片最大宽高比。 */
private const val maxCardAspectRatio = 2.2f

/** 投稿卡片默认宽高比。 */
private const val fallbackCardAspectRatio = 1f

private val waterfallFabMenuButtonHeight = 40.dp

data class SubmissionWaterfallPageControls(
    val paginationKind: SubmissionPaginationKind? = null,
    val currentPageNumber: Int? = null,
    val lastPageNumber: Int? = null,
    val showFirstPage: Boolean = false,
    val canLoadFirstPage: Boolean = false,
    val showPreviousPage: Boolean = false,
    val canLoadPreviousPage: Boolean = false,
    val showJumpToPage: Boolean = false,
    val canJumpToPage: Boolean = false,
    val showNextPage: Boolean = false,
    val canLoadNextPage: Boolean = false,
    val showLastPage: Boolean = false,
    val canLoadLastPage: Boolean = false,
    val loading: Boolean = false,
) {
  val showPageIndicator: Boolean
    get() = paginationKind == SubmissionPaginationKind.NUMBERED

  val hasAnyAction: Boolean
    get() = showFirstPage || showPreviousPage || showPageIndicator || showNextPage || showLastPage
}

data class SubmissionWaterfallViewportSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val anchorSid: Int?,
)

private data class SubmissionWaterfallFabAction(
    val icon: ImageVector? = null,
    val label: String? = null,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
) {
  val isIconOnly: Boolean
    get() = icon != null && label == null
}

/** 通用投稿瀑布流组件。 */
@Composable
fun SubmissionWaterfall(
    /** 投稿列表。 */
    items: List<SubmissionThumbnail>,
    /** 点击投稿回调。 */
    onItemClick: (SubmissionThumbnail) -> Unit,
    /** 当前可见最大索引上报。 */
    onLastVisibleIndexChanged: (Int) -> Unit,
    /** 是否还有下一页。 */
    canLoadMore: Boolean,
    /** 是否正在加载更多。 */
    loadingMore: Boolean,
    /** 追加错误文案。 */
    appendErrorMessage: String?,
    /** 手动重试回调。 */
    onRetryLoadMore: () -> Unit,
    /** 可选外部状态，用于保留滚动位置。 */
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    /** 单列最小宽度（dp）。 */
    minCardWidthDp: Int = defaultWaterfallCardMinWidthDp,
    /** 网格内边距。 */
    contentPadding: PaddingValues = waterfallPadding,
    /** 可选列表头部（整行）。 */
    headerContent: (@Composable () -> Unit)? = null,
    /** 可选头部附加项（整行，可多个）。 */
    preItemsContent: (LazyStaggeredGridScope.() -> Unit)? = null,
    /** 被屏蔽投稿在瀑布流中的展示策略。 */
    blockedSubmissionMode: BlockedSubmissionWaterfallMode = BlockedSubmissionWaterfallMode.SHOW,
    /** submission item 在整个 grid 中的起始偏移。 */
    itemIndexOffset: Int = 0,
    /** 分页导航控件。 */
    pageControls: SubmissionWaterfallPageControls? = null,
    /** 顶部是否可加载上一页。 */
    canLoadPreviousPageAtTop: Boolean = false,
    /** 顶部上一页加载状态。 */
    loadingPreviousPage: Boolean = false,
    /** 顶部上一页加载错误。 */
    prependErrorMessage: String? = null,
    /** 顶部加载上一页。 */
    onLoadPreviousPageAtTop: (() -> Unit)? = null,
    /** 加载第一页。 */
    onLoadFirstPage: (() -> Unit)? = null,
    /** 加载上一页。 */
    onLoadPreviousPage: (() -> Unit)? = null,
    /** 跳到某一页。 */
    onJumpToPage: ((Int) -> Unit)? = null,
    /** 加载下一页。 */
    onLoadNextPage: (() -> Unit)? = null,
    /** 加载最后一页。 */
    onLoadLastPage: (() -> Unit)? = null,
    /** 待消费的滚动请求。 */
    pendingScrollRequest: me.domino.fa2.ui.pages.submission.WaterfallScrollRequest? = null,
    /** 消费滚动请求。 */
    onConsumeScrollRequest: ((Long) -> Unit)? = null,
    /** waterfall 视口变化。 */
    onViewportChanged: ((SubmissionWaterfallViewportSnapshot) -> Unit)? = null,
) {
  val taxonomyRepository = LocalTaxonomyRepository.current
  val taxonomyCatalog = LocalTaxonomyCatalog.current
  val blockedRevealState = remember { mutableStateMapOf<Int, Boolean>() }
  val sourceIndexById =
      remember(items) { items.withIndex().associate { (index, item) -> item.id to index } }
  val latestOnViewportChanged = rememberUpdatedState(onViewportChanged)
  val latestOnConsumeScrollRequest = rememberUpdatedState(onConsumeScrollRequest)
  var navigationExpanded by rememberSaveable { mutableStateOf(false) }
  var jumpDialogVisible by rememberSaveable { mutableStateOf(false) }
  val prependHeaderVisible =
      canLoadPreviousPageAtTop || loadingPreviousPage || !prependErrorMessage.isNullOrBlank()
  val effectiveItemIndexOffset = itemIndexOffset + if (prependHeaderVisible) 1 else 0
  var jumpPageInput by
      rememberSaveable(pageControls?.currentPageNumber) {
        mutableStateOf(pageControls?.currentPageNumber?.toString().orEmpty())
      }

  LaunchedEffect(items) {
    val validIds = items.map { item -> item.id }.toSet()
    blockedRevealState.keys.retainAll(validIds)
  }

  val latestOnLastVisibleChanged = rememberUpdatedState(onLastVisibleIndexChanged)
  LaunchedEffect(state, sourceIndexById, items) {
    snapshotFlow {
          val maxVisibleSourceIndex =
              state.layoutInfo.visibleItemsInfo
                  .mapNotNull { info ->
                    val key = info.key as? Int ?: return@mapNotNull null
                    sourceIndexById[key]
                  }
                  .maxOrNull()
          when {
            items.isEmpty() -> 0
            !state.canScrollForward -> items.lastIndex
            maxVisibleSourceIndex != null -> maxVisibleSourceIndex
            else -> 0
          }
        }
        .distinctUntilChanged()
        .collect { lastVisible -> latestOnLastVisibleChanged.value(lastVisible) }
  }
  LaunchedEffect(state, sourceIndexById) {
    snapshotFlow {
          val visibleAnchors =
              state.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                val key = info.key as? Int ?: return@mapNotNull null
                key to info.offset.y
              }
          val anchorSid =
              (visibleAnchors.filter { (_, top) -> top >= 0 }.minByOrNull { (_, top) -> top }
                      ?: visibleAnchors.maxByOrNull { (_, top) -> top })
                  ?.first
          SubmissionWaterfallViewportSnapshot(
              firstVisibleItemIndex = state.firstVisibleItemIndex,
              firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
              anchorSid = anchorSid,
          )
        }
        .distinctUntilChanged()
        .collect { viewport -> latestOnViewportChanged.value?.invoke(viewport) }
  }
  LaunchedEffect(
      pendingScrollRequest?.version,
      pendingScrollRequest?.sid,
      items,
      effectiveItemIndexOffset,
  ) {
    val request = pendingScrollRequest ?: return@LaunchedEffect
    val targetSid =
        resolveWaterfallScrollTargetSid(
            request = request,
            itemIds = items.mapTo(linkedSetOf()) { item -> item.id },
            visibleItems =
                state.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                  (info.key as? Int)?.let { sid ->
                    WaterfallVisibleItem(
                        sid = sid,
                        laneOffsetX = info.offset.x,
                        itemOffsetY = info.offset.y,
                    )
                  }
                },
        )
    val itemIndex = items.indexOfFirst { item -> item.id == targetSid }
    if (itemIndex < 0) return@LaunchedEffect
    val targetIndex =
        (effectiveItemIndexOffset + itemIndex).coerceAtMost(
            effectiveItemIndexOffset + items.lastIndex
        )
    if (request.animated) {
      state.animateScrollToItem(targetIndex)
    } else {
      state.scrollToItem(targetIndex)
    }
    latestOnConsumeScrollRequest.value?.invoke(request.version)
  }

  Box(modifier = Modifier.fillMaxSize()) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minCardWidthDp.dp),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
      if (headerContent != null) {
        item(span = StaggeredGridItemSpan.FullLine) { headerContent() }
      }
      preItemsContent?.invoke(this)
      paginationHeader(
          canLoadPreviousPage = canLoadPreviousPageAtTop,
          loadingPreviousPage = loadingPreviousPage,
          prependErrorMessage = prependErrorMessage,
          onLoadPreviousPage = onLoadPreviousPageAtTop,
      )
      items(items = items, key = { item -> item.id }) { item ->
        val isBlockedRevealed = blockedRevealState[item.id] == true
        val blurredBlocked =
            item.isBlockedByTag &&
                blockedSubmissionMode == BlockedSubmissionWaterfallMode.BLUR_THEN_OPEN &&
                !isBlockedRevealed
        val categoryIconToken =
            remember(item.categoryTag, taxonomyCatalog) {
              taxonomyRepository.categoryCardIconByTag(item.categoryTag).orEmpty()
            }
        SubmissionWaterfallItem(
            item = item,
            categoryIconToken = categoryIconToken,
            blockedMaskActive = blurredBlocked,
            onClick = {
              if (blurredBlocked) {
                blockedRevealState[item.id] = true
              } else {
                onItemClick(item)
              }
            },
        )
      }
      paginationFooter(
          canLoadMore = canLoadMore,
          loadingMore = loadingMore,
          appendErrorMessage = appendErrorMessage,
          onRetryLoadMore = onRetryLoadMore,
      )
    }

    SubmissionWaterfallPageFab(
        controls = pageControls,
        expanded = navigationExpanded,
        onExpandedChange = { expanded -> navigationExpanded = expanded },
        onLoadFirstPage = onLoadFirstPage,
        onLoadPreviousPage = onLoadPreviousPage,
        onOpenJumpDialog = {
          jumpPageInput = pageControls?.currentPageNumber?.toString().orEmpty()
          jumpDialogVisible = true
        },
        onLoadNextPage = onLoadNextPage,
        onLoadLastPage = onLoadLastPage,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    )

    if (jumpDialogVisible) {
      val knownLastPageNumber = pageControls?.lastPageNumber
      val parsedPageNumber =
          jumpPageInput.toIntOrNull()?.takeIf { value ->
            value > 0 && (knownLastPageNumber == null || value <= knownLastPageNumber)
          }
      AlertDialog(
          onDismissRequest = { jumpDialogVisible = false },
          title = { Text(stringResource(Res.string.jump_to_page)) },
          text = {
            OutlinedTextField(
                value = jumpPageInput,
                onValueChange = { next -> jumpPageInput = next.filter(Char::isDigit) },
                label = {
                  Text(
                      knownLastPageNumber?.let { pageCount ->
                        stringResource(Res.string.target_page_number_with_range, pageCount)
                      } ?: stringResource(Res.string.target_page_number)
                  )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
          },
          confirmButton = {
            ExpressiveButton(
                onClick = {
                  parsedPageNumber?.let { pageNumber ->
                    onJumpToPage?.invoke(pageNumber)
                    jumpDialogVisible = false
                  }
                },
                enabled = parsedPageNumber != null,
            ) {
              Text(stringResource(Res.string.confirm))
            }
          },
          dismissButton = {
            ExpressiveTextButton(onClick = { jumpDialogVisible = false }) {
              Text(stringResource(Res.string.cancel))
            }
          },
      )
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun SubmissionWaterfallPageFab(
    controls: SubmissionWaterfallPageControls?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLoadFirstPage: (() -> Unit)?,
    onLoadPreviousPage: (() -> Unit)?,
    onOpenJumpDialog: (() -> Unit)?,
    onLoadNextPage: (() -> Unit)?,
    onLoadLastPage: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
  val effectiveControls = controls?.takeIf { it.hasAnyAction } ?: return
  val actions = buildList {
    if (effectiveControls.showFirstPage) {
      add(
          SubmissionWaterfallFabAction(
              icon = FaMaterialSymbols.Outlined.VerticalAlignTop,
              contentDescription = stringResource(Res.string.waterfall_first_page),
              enabled = effectiveControls.canLoadFirstPage && !effectiveControls.loading,
              onClick = { onLoadFirstPage?.invoke() },
          )
      )
    }
    if (effectiveControls.showPreviousPage) {
      add(
          SubmissionWaterfallFabAction(
              icon = FaMaterialSymbols.Outlined.ArrowUpwardAlt,
              contentDescription = stringResource(Res.string.waterfall_previous_page),
              enabled = effectiveControls.canLoadPreviousPage && !effectiveControls.loading,
              onClick = { onLoadPreviousPage?.invoke() },
          )
      )
    }
    if (effectiveControls.showPageIndicator) {
      val currentPageNumber = effectiveControls.currentPageNumber
      val lastPageNumber = effectiveControls.lastPageNumber
      val description =
          when {
            currentPageNumber != null && lastPageNumber != null ->
                stringResource(
                    Res.string.waterfall_page_number_with_total,
                    currentPageNumber,
                    lastPageNumber,
                )
            currentPageNumber != null ->
                stringResource(Res.string.waterfall_page_number, currentPageNumber)
            else -> stringResource(Res.string.waterfall_page_number_unknown)
          }
      add(
          SubmissionWaterfallFabAction(
              label =
                  when {
                    currentPageNumber != null ->
                        stringResource(Res.string.waterfall_page_number, currentPageNumber)
                    else -> stringResource(Res.string.waterfall_page_number_unknown)
                  },
              contentDescription = description,
              enabled =
                  effectiveControls.showJumpToPage &&
                      effectiveControls.canJumpToPage &&
                      !effectiveControls.loading,
              onClick = { onOpenJumpDialog?.invoke() },
          )
      )
    }
    if (effectiveControls.showNextPage) {
      add(
          SubmissionWaterfallFabAction(
              icon = FaMaterialSymbols.Outlined.ArrowDownwardAlt,
              contentDescription = stringResource(Res.string.waterfall_next_page),
              enabled = effectiveControls.canLoadNextPage && !effectiveControls.loading,
              onClick = { onLoadNextPage?.invoke() },
          )
      )
    }
    if (effectiveControls.showLastPage) {
      add(
          SubmissionWaterfallFabAction(
              icon = FaMaterialSymbols.Outlined.VerticalAlignBottom,
              contentDescription = stringResource(Res.string.waterfall_last_page),
              enabled = effectiveControls.canLoadLastPage && !effectiveControls.loading,
              onClick = { onLoadLastPage?.invoke() },
          )
      )
    }
  }
  FloatingActionButtonMenu(
      expanded = expanded,
      modifier = modifier,
      button = {
        ToggleFloatingActionButton(
            checked = expanded,
            onCheckedChange = onExpandedChange,
        ) {
          Icon(
              imageVector =
                  if (checkedProgress >= 0.5f) FaMaterialSymbols.Filled.Close
                  else FaMaterialSymbols.Filled.Add,
              contentDescription = stringResource(Res.string.waterfall_page_navigation),
          )
        }
      },
  ) {
    actions.forEach { action ->
      val containerColor =
          if (action.enabled) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          }
      val contentColor =
          if (action.enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          }
      if (action.isIconOnly) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
          Surface(
              onClick = { action.onClick() },
              enabled = action.enabled,
              modifier =
                  Modifier.size(waterfallFabMenuButtonHeight).semantics {
                    contentDescription = action.contentDescription
                  },
              shape = CircleShape,
              color = containerColor,
              contentColor = contentColor,
              tonalElevation = 0.dp,
              shadowElevation = 0.dp,
          ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              action.icon?.let { icon -> Icon(imageVector = icon, contentDescription = null) }
            }
          }
        }
      } else {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
          Surface(
              onClick = { action.onClick() },
              enabled = action.enabled,
              modifier = Modifier.semantics { contentDescription = action.contentDescription },
              shape = RoundedCornerShape(waterfallFabMenuButtonHeight / 2),
              color = containerColor,
              contentColor = contentColor,
              tonalElevation = 0.dp,
              shadowElevation = 0.dp,
          ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
              action.label?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }
      }
    }
  }
}

/** 瀑布流单卡片。 */
@Composable
private fun SubmissionWaterfallItem(
    /** 投稿数据。 */
    item: SubmissionThumbnail,
    /** taxonomy 分类 icon token。 */
    categoryIconToken: String,
    /** 是否显示屏蔽遮罩。 */
    blockedMaskActive: Boolean,
    /** 点击回调。 */
    onClick: () -> Unit,
) {
  Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border =
          BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
          ),
      modifier =
          Modifier.fillMaxWidth().testTag("submission-waterfall-card").clickable(onClick = onClick),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .aspectRatio(sanitizeCardAspectRatio(item.thumbnailAspectRatio))
                  .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
      ) {
        ThumbnailImage(
            url = item.thumbnailUrl,
            modifier =
                Modifier.fillMaxSize()
                    .then(
                        if (blockedMaskActive) {
                          Modifier.blur(22.dp)
                        } else {
                          Modifier
                        }
                    ),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.padding(8.dp),
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
                imageVector = categoryBadgeIcon(categoryIconToken),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(width = 16.dp, height = 16.dp),
            )
            Text(
                text = item.id.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Column(
          modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (item.authorAvatarUrl.isNotBlank()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(24.dp),
            ) {
              NetworkImage(
                  url = item.authorAvatarUrl,
                  modifier = Modifier.fillMaxSize().clip(CircleShape),
              )
            }
          } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
            ) {
              Text(
                  text = item.author.firstOrNull()?.uppercase() ?: "?",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
              )
            }
          }
          Text(
              text = item.author,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
          )
        }
      }
    }
  }
}

private fun categoryBadgeIcon(iconToken: String): ImageVector =
    when (iconToken) {
      "image" -> FaMaterialSymbols.Outlined.Image
      "movie" -> FaMaterialSymbols.Outlined.Movie
      "subject" -> FaMaterialSymbols.Outlined.Subject
      "music_note" -> FaMaterialSymbols.Outlined.MusicNote
      "inventory_2" -> FaMaterialSymbols.Outlined.Inventory2
      "category" -> FaMaterialSymbols.Outlined.Category
      else -> FaMaterialSymbols.Outlined.Category
    }

internal fun resolveWaterfallScrollTargetSid(
    request: me.domino.fa2.ui.pages.submission.WaterfallScrollRequest,
    itemIds: Set<Int>,
    visibleItems: List<WaterfallVisibleItem>,
): Int {
  val visibleLaneCount =
      visibleItems.map { item -> item.laneOffsetX }.distinct().size.coerceAtLeast(1)
  val visibleCandidateSid =
      request.targetPageLeadingSids
          .mapNotNull { candidateSid ->
            visibleItems
                .firstOrNull { item -> item.sid == candidateSid }
                ?.let { visibleItem -> candidateSid to visibleItem.itemOffsetY }
          }
          .maxByOrNull { (_, top) -> top }
          ?.first
  val fallbackLeadingCandidateSids = request.targetPageLeadingSids.take(visibleLaneCount)
  return visibleCandidateSid
      ?: fallbackLeadingCandidateSids.lastOrNull { sid -> sid in itemIds }
      ?: request.targetPageLeadingSids.firstOrNull { sid -> sid in itemIds }
      ?: request.sid
}

internal data class WaterfallVisibleItem(
    val sid: Int,
    val laneOffsetX: Int,
    val itemOffsetY: Int,
)

/** 统一分页状态 Footer。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyStaggeredGridScope.paginationHeader(
    canLoadPreviousPage: Boolean,
    loadingPreviousPage: Boolean,
    prependErrorMessage: String?,
    onLoadPreviousPage: (() -> Unit)?,
) {
  if (!canLoadPreviousPage && !loadingPreviousPage && prependErrorMessage.isNullOrBlank()) {
    return
  }
  item(key = "waterfall-prepend-header", span = StaggeredGridItemSpan.FullLine) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text =
                when {
                  loadingPreviousPage -> stringResource(Res.string.loading_previous_page_content)
                  !prependErrorMessage.isNullOrBlank() ->
                      stringResource(Res.string.auto_load_failed_manual_previous_page)
                  else ->
                      stringResource(
                          Res.string.continue_auto_load_previous_page_with_manual_fallback
                      )
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (loadingPreviousPage) {
          Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            LoadingIndicator(
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
          }
        } else if (canLoadPreviousPage && onLoadPreviousPage != null) {
          AssistChip(
              onClick = onLoadPreviousPage,
              label = {
                Text(
                    text =
                        if (!prependErrorMessage.isNullOrBlank()) {
                          stringResource(Res.string.manual_load_previous_page)
                        } else {
                          stringResource(Res.string.load_previous_page)
                        }
                )
              },
          )
        }
      }
    }
  }
}

/** 统一分页状态 Footer。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyStaggeredGridScope.paginationFooter(
    canLoadMore: Boolean,
    loadingMore: Boolean,
    appendErrorMessage: String?,
    onRetryLoadMore: () -> Unit,
) {
  item(span = StaggeredGridItemSpan.FullLine) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text =
                when {
                  loadingMore -> stringResource(Res.string.loading_more_content)
                  !appendErrorMessage.isNullOrBlank() && canLoadMore ->
                      stringResource(Res.string.auto_load_failed_manual_next_page)
                  canLoadMore ->
                      stringResource(Res.string.continue_auto_load_next_page_with_manual_fallback)
                  else -> stringResource(Res.string.reached_current_results_end)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canLoadMore && !loadingMore) {
          AssistChip(
              onClick = onRetryLoadMore,
              label = {
                Text(
                    text =
                        if (!appendErrorMessage.isNullOrBlank()) {
                          stringResource(Res.string.manual_load_next_page)
                        } else {
                          stringResource(Res.string.load_next_page)
                        }
                )
              },
          )
        } else if (loadingMore) {
          Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            LoadingIndicator(
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}

/** 规范化卡片宽高比，避免极端比例拉爆布局。 */
private fun sanitizeCardAspectRatio(
    /** 原始宽高比。 */
    rawRatio: Float
): Float {
  if (!rawRatio.isFinite() || rawRatio <= 0f) {
    return fallbackCardAspectRatio
  }
  return rawRatio.coerceIn(minCardAspectRatio, maxCardAspectRatio)
}
