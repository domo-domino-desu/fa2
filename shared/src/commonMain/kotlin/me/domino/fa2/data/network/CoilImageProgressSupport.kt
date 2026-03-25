package me.domino.fa2.data.network

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.BodyProgress
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.plugin

/** 给 Coil 安装可观测下载进度的 Ktor fetcher。 */
@OptIn(ExperimentalCoilApi::class)
fun ImageLoader.Builder.installCoilImageProgressSupport(
    /** 进度追踪器。 */
    progressTracker: ImageProgressTracker
): ImageLoader.Builder {
  val imageClient = createImageProgressHttpClient(progressTracker)
  return components { add(KtorNetworkFetcherFactory(httpClient = imageClient)) }
}

/** 构建带下载进度上报的图片专用 HttpClient。 */
private fun createImageProgressHttpClient(
    /** 进度追踪器。 */
    progressTracker: ImageProgressTracker
): HttpClient {
  val client = HttpClient {
    expectSuccess = false
    install(BodyProgress)
  }
  client.plugin(HttpSend).intercept { request ->
    val progressKey = normalizeProgressKey(request.url.toString())
    if (progressKey.isNotBlank()) {
      progressTracker.markRequestStarted(progressKey)
      request.onDownload { bytesRead, totalBytes ->
        progressTracker.updateDownloadProgress(progressKey, bytesRead, totalBytes ?: -1L)
      }
    }
    execute(request)
  }
  return client
}
