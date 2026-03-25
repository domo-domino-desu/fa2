package me.domino.fa2.ui.components.submission

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.BlockedSubmissionWaterfallMode
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.ui.components.ThumbnailImage
import me.domino.fa2.ui.icons.FaMaterialSymbols
import org.koin.compose.koinInject

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
) {
  val taxonomyRepository = koinInject<FaTaxonomyRepository>()
  val taxonomyCatalog by taxonomyRepository.catalog.collectAsState()
  val blockedRevealState = remember { mutableStateMapOf<Int, Boolean>() }
  val sourceIndexById =
      remember(items) { items.withIndex().associate { (index, item) -> item.id to index } }

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
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                  loadingMore -> "正在自动加载更多内容"
                  !appendErrorMessage.isNullOrBlank() && canLoadMore -> "自动加载失败，可手动加载下一页"
                  canLoadMore -> "继续浏览将自动加载下一页，未触发可手动加载"
                  else -> "已经到达当前结果的末尾"
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canLoadMore && !loadingMore) {
          AssistChip(
              onClick = onRetryLoadMore,
              label = {
                Text(text = if (!appendErrorMessage.isNullOrBlank()) "手动加载下一页" else "加载下一页")
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
                text = "加载中…",
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
