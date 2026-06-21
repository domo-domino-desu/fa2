package me.domino.fa2.desktop.e2e

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import io.ktor.client.HttpClient
import java.io.File
import me.domino.fa2.data.network.installCoilImageProgressSupport
import me.domino.fa2.di.KOIN_QUALIFIER_CACHED_DOWNLOAD_CLIENT
import me.domino.fa2.di.startAppKoin
import me.domino.fa2.di.stopAppKoin
import me.domino.fa2.ui.host.Fa2App
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named

/** E2E 测试中用于临时替换 user.home 系统属性的工具类。 */
internal class DesktopE2eHomeOverride(private val newHome: File) {
  /** 保存的原始 user.home 值，用于恢复时使用。 */
  private val originalHome: String? = System.getProperty("user.home")

  /** 将 user.home 设置为测试专用目录。 */
  fun apply() {
    System.setProperty("user.home", newHome.absolutePath)
  }

  /** 恢复 user.home 至测试前的原始值。 */
  fun restore() {
    if (originalHome == null) {
      System.clearProperty("user.home")
    } else {
      System.setProperty("user.home", originalHome)
    }
  }
}

/** E2E 测试运行时，负责启动和清理完整的应用测试环境。 */
internal class DesktopE2eRuntime(
    private val profile: DesktopE2eTestProfile,
    private val homeOverride: DesktopE2eHomeOverride,
    private val stores: DesktopE2eProfileStores,
) {
  /** 启动 Koin 依赖图并在 Compose 测试规则中渲染应用。 */
  fun start(composeRule: ComposeContentTestRule) {
    val koin = startAppKoin(desktopE2eTestPlatformModule(profile = profile, stores = stores))
    val cachedDownloadClient =
        koin.get<HttpClient>(qualifier = named(KOIN_QUALIFIER_CACHED_DOWNLOAD_CLIENT))
    composeRule.setContent {
      setSingletonImageLoaderFactory { platformContext ->
        ensureParentDir(profile.coilCacheDir)
        ImageLoader.Builder(platformContext)
            .installCoilImageProgressSupport(cachedDownloadClient = cachedDownloadClient)
            .diskCache {
              DiskCache.Builder()
                  .directory(profile.coilCacheDir.absolutePath.toPath())
                  .maxSizeBytes(testCoilDiskCacheMaxBytes)
                  .build()
            }
            .build()
      }
      Fa2App()
    }
  }

  /** 清理测试环境：清除 Cookie、停止 Koin、恢复 home 目录并删除临时文件。 */
  fun close() {
    runCatching { DesktopE2eSessionStorage.clearCookieVaultBlocking(stores.cookieVault) }
    stopAppKoin()
    homeOverride.restore()
    runCatching { profile.rootDir.deleteRecursively() }
  }

  companion object {
    /** 根据会话快照创建并初始化测试运行时实例。 */
    fun create(snapshot: DesktopE2eSessionSnapshot): DesktopE2eRuntime {
      val profile = DesktopE2eTestProfile.create()
      val homeOverride = DesktopE2eHomeOverride(profile.rootDir)
      homeOverride.apply()
      val stores = createProfileStores(profile)
      DesktopE2eSessionStorage.seedProfileBlocking(stores = stores, snapshot = snapshot)
      return DesktopE2eRuntime(profile = profile, homeOverride = homeOverride, stores = stores)
    }
  }
}
