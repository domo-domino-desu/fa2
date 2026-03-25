package me.domino.fa2.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import me.domino.fa2.data.network.ImageDownloadProgressSnapshot
import me.domino.fa2.data.network.ImageProgressTracker
import me.domino.fa2.data.network.normalizeProgressKey
import org.koin.compose.koinInject

/** 图片加载生命周期。 */
enum class ImageLoadLifecycleState {
  /** 初始状态。 */
  Idle,
  /** 正在加载。 */
  Loading,
  /** 加载成功。 */
  Success,
  /** 加载失败。 */
  Error,
}

/** 图片加载进度状态。 */
sealed interface ImageLoadProgressState {
  /** 初始状态。 */
  data object Idle : ImageLoadProgressState

  /** 加载中但总量未知。 */
  data object LoadingUnknown : ImageLoadProgressState

  /** 加载中且总量已知。 */
  data class LoadingKnown(
      /** 归一化进度。 */
      val progress: Float,
      /** 已下载字节。 */
      val bytesRead: Long,
      /** 总字节。 */
      val totalBytes: Long,
  ) : ImageLoadProgressState

  /** 加载成功。 */
  data object Success : ImageLoadProgressState

  /** 加载失败。 */
  data object Error : ImageLoadProgressState
}

/** 合并生命周期与字节进度，得到统一渲染状态。 */
internal fun resolveImageLoadProgressState(
    /** 生命周期状态。 */
    lifecycleState: ImageLoadLifecycleState,
    /** 字节进度快照。 */
    progressSnapshot: ImageDownloadProgressSnapshot?,
): ImageLoadProgressState =
    when (lifecycleState) {
      ImageLoadLifecycleState.Idle -> ImageLoadProgressState.Idle
      ImageLoadLifecycleState.Success -> ImageLoadProgressState.Success
      ImageLoadLifecycleState.Error -> ImageLoadProgressState.Error
      ImageLoadLifecycleState.Loading -> {
        val progress = progressSnapshot?.progressFraction
        if (progress != null) {
          ImageLoadProgressState.LoadingKnown(
              progress = progress,
              bytesRead = progressSnapshot.bytesRead,
              totalBytes = progressSnapshot.totalBytes,
          )
        } else {
          ImageLoadProgressState.LoadingUnknown
        }
      }
    }

/** 组合层统一图片加载进度状态。 */
@Composable
internal fun rememberImageLoadProgressState(
    /** 进度 key。 */
    progressKey: String?,
    /** 当前加载生命周期。 */
    lifecycleState: ImageLoadLifecycleState,
    /** 追踪器（默认来自 Koin）。 */
    tracker: ImageProgressTracker = koinInject(),
): ImageLoadProgressState {
  val normalizedKey =
      remember(progressKey) {
        progressKey?.let(::normalizeProgressKey)?.takeIf { key -> key.isNotBlank() }
      }
  val progressFlow =
      remember(tracker, normalizedKey) {
        normalizedKey?.let { key -> tracker.observe(key) } ?: flowOf(null)
      }
  val progressSnapshot by progressFlow.collectAsState(initial = null)

  LaunchedEffect(tracker, normalizedKey, lifecycleState) {
    val key = normalizedKey ?: return@LaunchedEffect
    if (
        lifecycleState == ImageLoadLifecycleState.Success ||
            lifecycleState == ImageLoadLifecycleState.Error
    ) {
      tracker.clear(key)
    }
  }

  return remember(lifecycleState, progressSnapshot) {
    resolveImageLoadProgressState(
        lifecycleState = lifecycleState,
        progressSnapshot = progressSnapshot,
    )
  }
}

/** 顶部直线加载条（非 wavy）。 */
@Composable
internal fun TopLinearImageLoadingProgress(
    /** 加载进度状态。 */
    progressState: ImageLoadProgressState,
    /** 组件修饰符。 */
    modifier: Modifier = Modifier,
) {
  val indicatorModifier = modifier.height(2.dp)
  when (progressState) {
    is ImageLoadProgressState.LoadingKnown ->
        LinearProgressIndicator(
            progress = { progressState.progress },
            modifier = indicatorModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        )

    ImageLoadProgressState.LoadingUnknown ->
        LinearProgressIndicator(
            modifier = indicatorModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        )

    else -> Unit
  }
}

/** 居中圆形 wavy 加载条。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CenterCircularWavyImageLoadingProgress(
    /** 加载进度状态。 */
    progressState: ImageLoadProgressState,
    /** 组件修饰符。 */
    modifier: Modifier = Modifier,
) {
  val indicatorModifier = modifier.size(52.dp)
  when (progressState) {
    is ImageLoadProgressState.LoadingKnown ->
        CircularWavyProgressIndicator(
            progress = { progressState.progress },
            modifier = indicatorModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        )

    ImageLoadProgressState.LoadingUnknown ->
        CircularWavyProgressIndicator(
            modifier = indicatorModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        )

    else -> Unit
  }
}
