package me.domino.fa2.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.gif.GifDecoder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.domino.fa2.ui.host.Fa2App
import okio.Path.Companion.toPath

private const val coilDiskCacheMaxBytes = 1024L * 1024L * 1024L
private val supportedFaHosts = setOf("furaffinity.net", "www.furaffinity.net")

/** Android 入口 Activity。 */
class MainActivity : ComponentActivity() {
  private val externalFaLinkEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)

  /**
   * 生命周期创建回调。
   *
   * @param savedInstanceState 恢复状态。
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      emitExternalFaLink(intent)
    }
    enableEdgeToEdge()
    setContent {
      setSingletonImageLoaderFactory { platformContext ->
        ImageLoader.Builder(platformContext)
            .components { add(GifDecoder.Factory()) }
            .diskCache {
              DiskCache.Builder()
                  .directory("${platformContext.cacheDir.absolutePath}/coil-image-cache".toPath())
                  .maxSizeBytes(coilDiskCacheMaxBytes)
                  .build()
            }
            .build()
      }
      Fa2App(externalFaLinkEvents = externalFaLinkEvents.asSharedFlow())
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    emitExternalFaLink(intent)
  }

  private fun emitExternalFaLink(intent: Intent?) {
    val deepLinkUri = extractExternalFaLink(intent) ?: return
    externalFaLinkEvents.tryEmit(deepLinkUri.toString())
  }

  private fun extractExternalFaLink(intent: Intent?): Uri? {
    if (intent?.action != Intent.ACTION_VIEW) return null
    val data = intent.data ?: return null
    val scheme = data.scheme?.lowercase() ?: return null
    if (scheme != "https" && scheme != "http") return null
    val host = data.host?.lowercase() ?: return null
    if (host !in supportedFaHosts) return null
    return data
  }
}
