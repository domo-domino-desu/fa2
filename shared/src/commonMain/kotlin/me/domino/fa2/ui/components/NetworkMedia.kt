package me.domino.fa2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import me.domino.fa2.util.logging.FaLog

private val networkImageLog = FaLog.withTag("NetworkImage")

/** 圆形头像组件。头像 URL 为空或加载失败时回退显示名称首字母。 */
@Composable
fun AvatarImage(
    /** 头像地址。 */
    url: String,
    /** 用于占位首字母的显示名。 */
    displayName: String,
    /** 组件修饰符。 */
    modifier: Modifier = Modifier,
    /** 图片说明。 */
    contentDescription: String? = null,
    /** 头像尺寸。 */
    size: Dp = 40.dp,
    /** 占位首字母文本样式。 */
    placeholderTextStyle: TextStyle = MaterialTheme.typography.labelSmall,
    /** 是否展示加载占位层。 */
    showLoadingPlaceholder: Boolean = false,
) {
  var failedUrl by remember { mutableStateOf<String?>(null) }
  val normalizedUrl = remember(url) { normalizeNetworkUrl(url).trim() }
  val shouldShowImage = normalizedUrl.isNotBlank() && failedUrl != normalizedUrl
  Surface(
      shape = CircleShape,
      color =
          if (shouldShowImage) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
          } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
          },
      modifier = modifier.size(size),
  ) {
    if (shouldShowImage) {
      NetworkImage(
          url = normalizedUrl,
          modifier = Modifier.fillMaxSize().clip(CircleShape),
          contentDescription = contentDescription,
          contentScale = ContentScale.Crop,
          showLoadingPlaceholder = showLoadingPlaceholder,
          onLoadError = { failedUrl = normalizedUrl },
      )
    } else {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = displayName.firstOrNull()?.uppercase() ?: "?",
            style = placeholderTextStyle,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
      }
    }
  }
}

/** 缩略图组件。 */
@Composable
fun ThumbnailImage(
    /** 图片地址。 */
    url: String,
    /** 组件修饰符。 */
    modifier: Modifier = Modifier,
    /** 图片说明。 */
    contentDescription: String? = null,
    /** 是否展示加载占位层。 */
    showLoadingPlaceholder: Boolean = true,
) {
  NetworkImage(
      url = url,
      modifier = modifier,
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      showLoadingPlaceholder = showLoadingPlaceholder,
  )
}

/** 通用网络图片组件。 */
@Composable
fun NetworkImage(
    /** 图片地址。 */
    url: String,
    /** 组件修饰符。 */
    modifier: Modifier = Modifier,
    /** 图片说明。 */
    contentDescription: String? = null,
    /** 图片缩放模式。 */
    contentScale: ContentScale = ContentScale.Fit,
    /** 加载主图时用于占位的缩略图地址。 */
    thumbnailUrl: String? = null,
    /** 是否展示加载占位层。 */
    showLoadingPlaceholder: Boolean = true,
    /** 是否显示顶部直线进度条。 */
    showTopLinearLoadingProgress: Boolean = false,
    /** 自定义进度 key（默认使用主图 URL）。 */
    progressTrackingKey: String? = null,
    /** 图片加载失败回调。 */
    onLoadError: () -> Unit = {},
) {
  val platformContext = LocalPlatformContext.current
  val normalizedUrl = remember(url) { normalizeNetworkUrl(url).trim() }
  val normalizedThumbnailUrl =
      remember(thumbnailUrl) {
        thumbnailUrl?.let(::normalizeNetworkUrl)?.trim()?.takeIf { it.isNotBlank() }
      }
  if (normalizedUrl.isBlank()) {
    if (!normalizedThumbnailUrl.isNullOrBlank()) {
      networkImageLog.w { "图片 URL 为空，回退加载缩略图 -> thumbnailUrl=$normalizedThumbnailUrl" }
      NetworkImage(
          url = normalizedThumbnailUrl,
          modifier = modifier,
          contentDescription = contentDescription,
          contentScale = contentScale,
          showLoadingPlaceholder = showLoadingPlaceholder,
          showTopLinearLoadingProgress = false,
          thumbnailUrl = null,
      )
      return
    }
    networkImageLog.w { "图片 URL 为空，无法发起加载" }
    if (showLoadingPlaceholder) {
      Box(
          modifier =
              modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
      )
    } else {
      Box(modifier = modifier)
    }
    return
  }
  val request =
      remember(platformContext, normalizedUrl) {
        ImageRequest.Builder(platformContext).data(normalizedUrl).build()
      }
  val resolvedProgressKey =
      remember(showTopLinearLoadingProgress, progressTrackingKey, normalizedUrl) {
        if (!showTopLinearLoadingProgress) {
          null
        } else {
          normalizeNetworkUrl(progressTrackingKey ?: normalizedUrl).trim().ifBlank { null }
        }
      }
  var loadLifecycleState by
      remember(resolvedProgressKey) { mutableStateOf(ImageLoadLifecycleState.Idle) }
  val progressState =
      if (showTopLinearLoadingProgress) {
        rememberImageLoadProgressState(
            progressKey = resolvedProgressKey,
            lifecycleState = loadLifecycleState,
        )
      } else {
        ImageLoadProgressState.Idle
      }

  Box(modifier = modifier) {
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        contentScale = contentScale,
        filterQuality = FilterQuality.High,
        onLoading = {
          loadLifecycleState = ImageLoadLifecycleState.Loading
          networkImageLog.d {
            "图片 UI 开始加载 -> url=$normalizedUrl, thumbnailUrl=${normalizedThumbnailUrl ?: "-"}, " +
                "progressKey=${resolvedProgressKey ?: "-"}"
          }
        },
        onSuccess = {
          loadLifecycleState = ImageLoadLifecycleState.Success
          networkImageLog.d { "图片 UI 加载成功 -> url=$normalizedUrl" }
        },
        onError = { state ->
          loadLifecycleState = ImageLoadLifecycleState.Error
          networkImageLog.e(state.result.throwable) { "图片 UI 加载失败 -> url=$normalizedUrl" }
          onLoadError()
        },
        loading = {
          if (!normalizedThumbnailUrl.isNullOrBlank() && normalizedThumbnailUrl != normalizedUrl) {
            networkImageLog.d {
              "图片 UI 加载中显示缩略图 -> url=$normalizedUrl, thumbnailUrl=$normalizedThumbnailUrl"
            }
            NetworkImage(
                url = normalizedThumbnailUrl,
                modifier = Modifier.fillMaxSize(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                showLoadingPlaceholder = showLoadingPlaceholder,
                showTopLinearLoadingProgress = false,
                thumbnailUrl = null,
            )
          } else if (showLoadingPlaceholder) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            )
          } else {
            Box(modifier = Modifier.fillMaxSize())
          }
        },
        error = {
          if (!normalizedThumbnailUrl.isNullOrBlank() && normalizedThumbnailUrl != normalizedUrl) {
            networkImageLog.w {
              "图片 UI 加载失败后显示缩略图 -> url=$normalizedUrl, thumbnailUrl=$normalizedThumbnailUrl"
            }
            NetworkImage(
                url = normalizedThumbnailUrl,
                modifier = Modifier.fillMaxSize(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                showLoadingPlaceholder = showLoadingPlaceholder,
                showTopLinearLoadingProgress = false,
                thumbnailUrl = null,
            )
          } else if (showLoadingPlaceholder) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            )
          } else {
            Box(modifier = Modifier.fillMaxSize())
          }
        },
        success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
    )
    if (showTopLinearLoadingProgress) {
      TopLinearImageLoadingProgress(
          progressState = progressState,
          modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

/** 规范化网络地址，兼容 `//` 开头的 URL。 */
private fun normalizeNetworkUrl(
    /** 原始地址。 */
    url: String
): String = if (url.startsWith("//")) "https:$url" else url
