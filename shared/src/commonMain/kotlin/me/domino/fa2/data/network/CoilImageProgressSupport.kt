package me.domino.fa2.data.network

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient

/** 给 Coil 安装可观测下载进度的 Ktor fetcher。 */
@OptIn(ExperimentalCoilApi::class)
fun ImageLoader.Builder.installCoilImageProgressSupport(
    /** 图片、OCR、附件下载共用的缓存 HTTP 客户端。 */
    cachedDownloadClient: HttpClient
): ImageLoader.Builder {
  return components { add(KtorNetworkFetcherFactory(httpClient = cachedDownloadClient)) }
}
