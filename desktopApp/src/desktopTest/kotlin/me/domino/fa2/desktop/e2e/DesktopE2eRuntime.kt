package me.domino.fa2.desktop.e2e

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import java.io.File
import me.domino.fa2.data.network.ImageProgressTracker
import me.domino.fa2.data.network.installCoilImageProgressSupport
import me.domino.fa2.di.startAppKoin
import me.domino.fa2.di.stopAppKoin
import me.domino.fa2.ui.host.Fa2App
import okio.Path.Companion.toPath

internal class DesktopE2eHomeOverride(private val newHome: File) {
  private val originalHome: String? = System.getProperty("user.home")

  fun apply() {
    System.setProperty("user.home", newHome.absolutePath)
  }

  fun restore() {
    if (originalHome == null) {
      System.clearProperty("user.home")
    } else {
      System.setProperty("user.home", originalHome)
    }
  }
}

internal class DesktopE2eRuntime(
    private val profile: DesktopE2eTestProfile,
    private val homeOverride: DesktopE2eHomeOverride,
    private val stores: DesktopE2eProfileStores,
) {
  fun start(composeRule: ComposeContentTestRule) {
    val koin = startAppKoin(desktopE2eTestPlatformModule(profile = profile, stores = stores))
    val progressTracker = koin.get<ImageProgressTracker>()
    composeRule.setContent {
      setSingletonImageLoaderFactory { platformContext ->
        ensureParentDir(profile.coilCacheDir)
        ImageLoader.Builder(platformContext)
            .installCoilImageProgressSupport(progressTracker)
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

  fun close() {
    runCatching { DesktopE2eSessionStorage.clearCookieVaultBlocking(stores.cookieVault) }
    stopAppKoin()
    homeOverride.restore()
    runCatching { profile.rootDir.deleteRecursively() }
  }

  companion object {
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
