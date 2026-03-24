package me.domino.fa2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest

/** 缩略图组件。 */
@Composable
fun ThumbnailImage(
  /** 图片地址。 */
  url: String,
  /** 组件修饰符。 */
  modifier: Modifier = Modifier,
  /** 是否展示加载占位层。 */
  showLoadingPlaceholder: Boolean = true,
) {
  NetworkImage(
    url = url,
    modifier = modifier,
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
  /** 图片缩放模式。 */
  contentScale: ContentScale = ContentScale.Fit,
  /** 加载主图时用于占位的缩略图地址。 */
  thumbnailUrl: String? = null,
  /** 是否展示加载占位层。 */
  showLoadingPlaceholder: Boolean = true,
) {
  val platformContext = LocalPlatformContext.current
  val normalizedUrl = remember(url) { normalizeNetworkUrl(url).trim() }
  val normalizedThumbnailUrl =
    remember(thumbnailUrl) {
      thumbnailUrl?.let(::normalizeNetworkUrl)?.trim()?.takeIf { it.isNotBlank() }
    }
  if (normalizedUrl.isBlank()) {
    if (!normalizedThumbnailUrl.isNullOrBlank()) {
      NetworkImage(
        url = normalizedThumbnailUrl,
        modifier = modifier,
        contentScale = contentScale,
        showLoadingPlaceholder = showLoadingPlaceholder,
        thumbnailUrl = null,
      )
      return
    }
    if (showLoadingPlaceholder) {
      Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
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

  SubcomposeAsyncImage(
    model = request,
    contentDescription = null,
    modifier = modifier,
    contentScale = contentScale,
    filterQuality = FilterQuality.High,
    loading = {
      if (!normalizedThumbnailUrl.isNullOrBlank() && normalizedThumbnailUrl != normalizedUrl) {
        NetworkImage(
          url = normalizedThumbnailUrl,
          modifier = Modifier.fillMaxSize(),
          contentScale = contentScale,
          showLoadingPlaceholder = showLoadingPlaceholder,
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
        NetworkImage(
          url = normalizedThumbnailUrl,
          modifier = Modifier.fillMaxSize(),
          contentScale = contentScale,
          showLoadingPlaceholder = showLoadingPlaceholder,
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
}

/** 规范化网络地址，兼容 `//` 开头的 URL。 */
private fun normalizeNetworkUrl(
  /** 原始地址。 */
  url: String
): String = if (url.startsWith("//")) "https:$url" else url
