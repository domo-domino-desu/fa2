package me.domino.fa2.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 通用瀑布流骨架屏。
 */
@Composable
fun WaterfallLoadingSkeleton(
    minCardWidthDp: Int,
    state: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
    itemCount: Int = 48,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    headerContent: (@Composable () -> Unit)? = null,
    preItemsContent: (LazyStaggeredGridScope.() -> Unit)? = null,
) {
    val skeletonHeights = remember(itemCount) {
        List(itemCount) { index -> index to Random.nextInt(164, 308).dp }
    }
    LazyVerticalStaggeredGrid(
        state = state,
        columns = StaggeredGridCells.Adaptive(minCardWidthDp.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        if (headerContent != null) {
            item(
                key = "waterfall-loading-skeleton-header",
                span = StaggeredGridItemSpan.FullLine,
            ) {
                headerContent()
            }
        }
        preItemsContent?.invoke(this)
        items(
            items = skeletonHeights,
            key = { (index, _) -> index },
        ) { (_, blockHeight) ->
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(blockHeight),
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}
