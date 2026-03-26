package me.domino.fa2.desktop.e2e

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.anifantakis.lib.ksafe.KSafe
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.random.Random
import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.data.local.AppDatabaseBuilderFactory
import me.domino.fa2.di.KOIN_QUALIFIER_COOKIE_VAULT
import me.domino.fa2.di.KOIN_QUALIFIER_SETTINGS_SECRET_VAULT
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val tempPrefix = "fa2-desktop-e2e-"
private const val startupTimeoutMs = 90_000L
private const val routeTimeoutMs = 60_000L
private const val defaultPollIntervalMs = 250L

internal const val realCookieVaultFileName = KOIN_QUALIFIER_COOKIE_VAULT
internal const val testCoilDiskCacheMaxBytes = 256L * 1024L * 1024L
internal const val desktopE2eStartupTimeoutMs: Long = startupTimeoutMs
internal const val desktopE2eRouteTimeoutMs: Long = routeTimeoutMs
internal const val desktopE2eDefaultPollIntervalMs: Long = defaultPollIntervalMs

internal data class DesktopE2eSessionSnapshot(
    val cookieHeader: String,
    val userAgent: String,
    val username: String?,
)

internal sealed interface DesktopE2ePreflightResult {
  data class Ready(val snapshot: DesktopE2eSessionSnapshot) : DesktopE2ePreflightResult

  data class Skip(val message: String) : DesktopE2ePreflightResult

  data class Fail(val message: String) : DesktopE2ePreflightResult
}

internal data class DesktopE2eTestProfile(
    val rootDir: File,
    val dataStorePath: File,
    val databasePath: File,
    val coilCacheDir: File,
    val cookieVaultFileName: String,
) {
  companion object {
    fun create(): DesktopE2eTestProfile {
      val root = Files.createTempDirectory(tempPrefix).toFile()
      return DesktopE2eTestProfile(
          rootDir = root,
          dataStorePath = File(root, ".config/fa2/datastore/settings.preferences_pb"),
          databasePath = File(root, ".cache/fa2/room-cache/fa2-cache.db"),
          coilCacheDir = File(root, ".cache/fa2/coil-image-cache"),
          cookieVaultFileName = "fa2_desktop_e2e_${UUID.randomUUID().toString().replace("-", "_")}",
      )
    }
  }
}

internal data class DesktopE2eProfileStores(
    val dataStore: DataStore<Preferences>,
    val cookieVault: KSafe,
    val settingsSecretVault: KSafe,
)

internal object DesktopE2eThrottle {
  private val random = Random(System.currentTimeMillis())

  fun pauseBeforeStartup() {
    pause(label = "startup", minMs = 1_800L, maxMs = 3_200L)
  }

  fun pauseBeforeAction(label: String) {
    pause(label = label, minMs = 1_200L, maxMs = 2_400L)
  }

  fun pauseAfterTest() {
    pause(label = "cooldown", minMs = 4_000L, maxMs = 7_000L)
  }

  private fun pause(label: String, minMs: Long, maxMs: Long) {
    val durationMs = random.nextLong(from = minMs, until = maxMs + 1L)
    println("DesktopE2E throttle[$label] -> sleep ${durationMs}ms")
    Thread.sleep(durationMs)
  }
}

internal fun desktopE2eTestPlatformModule(
    profile: DesktopE2eTestProfile,
    stores: DesktopE2eProfileStores,
): Module = module {
  single<AppDatabaseBuilderFactory> {
    AppDatabaseBuilderFactory {
      ensureParentFile(profile.databasePath)
      Room.databaseBuilder<AppDatabase>(name = profile.databasePath.absolutePath)
          .setDriver(BundledSQLiteDriver())
    }
  }
  single<DataStore<Preferences>> { stores.dataStore }
  single(named(KOIN_QUALIFIER_COOKIE_VAULT)) { stores.cookieVault }
  single(named(KOIN_QUALIFIER_SETTINGS_SECRET_VAULT)) { stores.settingsSecretVault }
}

internal fun createProfileStores(profile: DesktopE2eTestProfile): DesktopE2eProfileStores {
  ensureParentFile(profile.dataStorePath)
  ensureParentFile(profile.databasePath)
  ensureParentDir(profile.coilCacheDir)
  return DesktopE2eProfileStores(
      dataStore = createPreferencesDataStore(profile.dataStorePath),
      cookieVault = KSafe(fileName = profile.cookieVaultFileName),
      settingsSecretVault =
          KSafe(fileName = "${profile.cookieVaultFileName}_settings_secret_vault"),
  )
}

internal fun realDesktopPreferencesFile(): File {
  val userHome = System.getProperty("user.home").orEmpty().ifBlank { "." }
  return File(File(userHome, ".config/fa2/datastore"), "settings.preferences_pb")
}

internal fun createPreferencesDataStore(file: File): DataStore<Preferences> {
  ensureParentFile(file)
  return PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
}

internal fun ensureParentFile(file: File) {
  ensureParentDir(checkNotNull(file.parentFile) { "Missing parent dir for ${file.absolutePath}" })
}

internal fun ensureParentDir(dir: File) {
  if (!dir.exists()) {
    check(dir.mkdirs()) { "Unable to create directory: ${dir.absolutePath}" }
  }
  check(dir.isDirectory) { "Invalid directory: ${dir.absolutePath}" }
}
