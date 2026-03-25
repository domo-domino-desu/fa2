package me.domino.fa2.desktop.e2e

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import eu.anifantakis.lib.ksafe.KSafe
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.random.Random
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.data.local.AppDatabaseBuilderFactory
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.model.AuthProbeResult
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.ImageProgressTracker
import me.domino.fa2.data.network.KSafeCookiePersistence
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.network.installCoilImageProgressSupport
import me.domino.fa2.data.repository.AuthRepository
import me.domino.fa2.di.KOIN_QUALIFIER_COOKIE_VAULT
import me.domino.fa2.di.startAppKoin
import me.domino.fa2.di.stopAppKoin
import me.domino.fa2.ui.host.Fa2App
import okio.Path.Companion.toPath
import org.junit.AssumptionViolatedException
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val tempPrefix = "fa2-desktop-e2e-"
private const val realCookieVaultFileName = KOIN_QUALIFIER_COOKIE_VAULT
private const val defaultPollIntervalMs = 250L
private const val startupTimeoutMs = 90_000L
private const val routeTimeoutMs = 60_000L
private const val testCoilDiskCacheMaxBytes = 256L * 1024L * 1024L

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
    startAppKoin(desktopE2eTestPlatformModule(profile = profile, stores = stores))
    composeRule.setContent {
      setSingletonImageLoaderFactory { platformContext ->
        val progressTracker = GlobalContext.get().get<ImageProgressTracker>()
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
    runCatching { runBlocking { stores.cookieVault.clearAll() } }
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
      seedProfile(stores = stores, snapshot = snapshot)
      return DesktopE2eRuntime(profile = profile, homeOverride = homeOverride, stores = stores)
    }

    private fun seedProfile(stores: DesktopE2eProfileStores, snapshot: DesktopE2eSessionSnapshot) {
      val cookiesStorage =
          FaCookiesStorage(KSafeCookiePersistence(cookieVault = stores.cookieVault))
      val userAgentStorage = UserAgentStorage(KeyValueStorage(stores.dataStore))
      runBlocking {
        cookiesStorage.saveRawCookieHeader(snapshot.cookieHeader)
        userAgentStorage.saveOverride(snapshot.userAgent)
      }
    }
  }
}

internal object DesktopE2eChallengePolicy {
  private const val challengeTag = "cf-challenge-status"

  @OptIn(ExperimentalTestApi::class)
  fun waitUntilTagExistsOrSkip(
      rule: ComposeContentTestRule,
      tag: String,
      description: String,
      timeoutMillis: Long = routeTimeoutMs,
  ) {
    waitUntilOrSkip(rule = rule, description = description, timeoutMillis = timeoutMillis) {
      hasNode(rule = rule, matcher = hasTestTag(tag), useUnmergedTree = true)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  fun waitUntilTextExistsOrSkip(
      rule: ComposeContentTestRule,
      text: String,
      description: String,
      timeoutMillis: Long = routeTimeoutMs,
  ) {
    waitUntilOrSkip(rule = rule, description = description, timeoutMillis = timeoutMillis) {
      hasNode(rule = rule, matcher = hasText(text), useUnmergedTree = true)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  fun waitUntilAnyNodeWithTagExistsOrSkip(
      rule: ComposeContentTestRule,
      tag: String,
      description: String,
      timeoutMillis: Long = routeTimeoutMs,
  ) {
    waitUntilOrSkip(rule = rule, description = description, timeoutMillis = timeoutMillis) {
      hasNode(rule = rule, matcher = hasTestTag(tag), useUnmergedTree = true)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  fun ensureNoAuthScreen(rule: ComposeContentTestRule) {
    if (hasNode(rule = rule, matcher = hasTestTag("auth-screen"), useUnmergedTree = true)) {
      fail(
          "Desktop e2e unexpectedly stayed on auth screen after local-session preflight succeeded."
      )
    }
  }

  @OptIn(ExperimentalTestApi::class)
  private fun waitUntilOrSkip(
      rule: ComposeContentTestRule,
      description: String,
      timeoutMillis: Long,
      condition: () -> Boolean,
  ) {
    val startAt = System.currentTimeMillis()
    while (System.currentTimeMillis() - startAt <= timeoutMillis) {
      rule.waitForIdle()
      if (isChallengeVisible(rule)) {
        throw AssumptionViolatedException(
            "Cloudflare challenge encountered while waiting for $description."
        )
      }
      if (condition()) {
        return
      }
      Thread.sleep(defaultPollIntervalMs)
    }
    fail("Timed out waiting for $description within ${timeoutMillis}ms.")
  }

  @OptIn(ExperimentalTestApi::class)
  private fun isChallengeVisible(rule: ComposeContentTestRule): Boolean =
      hasNode(rule = rule, matcher = hasTestTag(challengeTag), useUnmergedTree = true) ||
          hasNode(
              rule = rule,
              matcher = hasText("Cloudflare", substring = true, ignoreCase = true),
              useUnmergedTree = true,
          )

  private fun hasNode(
      rule: ComposeContentTestRule,
      matcher: SemanticsMatcher,
      useUnmergedTree: Boolean,
  ): Boolean =
      runCatching {
            rule
                .onAllNodes(matcher = matcher, useUnmergedTree = useUnmergedTree)
                .fetchSemanticsNodes()
          }
          .getOrDefault(emptyList<SemanticsNode>())
          .isNotEmpty()
}

internal object DesktopE2eSessionLoader {
  fun preflight(): DesktopE2ePreflightResult {
    val originalHome = System.getProperty("user.home").orEmpty().ifBlank { "." }
    val realCookieHeader = runBlocking { readRealCookieHeader() }.trim()
    if (realCookieHeader.isBlank()) {
      return DesktopE2ePreflightResult.Fail(
          "No persisted desktop session cookie was found in $originalHome."
      )
    }

    val persistedUserAgent = runBlocking { readRealPersistedUserAgent() }
    val snapshot =
        DesktopE2eSessionSnapshot(
            cookieHeader = realCookieHeader,
            userAgent = persistedUserAgent.ifBlank { UserAgentStorage.DEFAULT_USER_AGENT },
            username = null,
        )

    val probeProfile = DesktopE2eTestProfile.create()
    return try {
      val result = probeCopiedSession(snapshot = snapshot, profile = probeProfile)
      when (result) {
        is AuthProbeResult.LoggedIn -> {
          DesktopE2ePreflightResult.Ready(snapshot.copy(username = result.username))
        }

        is AuthProbeResult.AuthInvalid -> {
          DesktopE2ePreflightResult.Fail(
              "Persisted desktop session is present but invalid: ${result.message}"
          )
        }

        is AuthProbeResult.Error -> {
          if (result.message.contains("Cloudflare", ignoreCase = true)) {
            DesktopE2ePreflightResult.Skip(
                "Desktop e2e skipped because preflight hit Cloudflare: ${result.message}"
            )
          } else {
            DesktopE2ePreflightResult.Fail(
                "Desktop e2e preflight failed before launch: ${result.message}"
            )
          }
        }
      }
    } finally {
      runCatching { probeProfile.rootDir.deleteRecursively() }
    }
  }

  private suspend fun readRealCookieHeader(): String {
    val cookiePersistence =
        KSafeCookiePersistence(cookieVault = KSafe(fileName = realCookieVaultFileName))
    return cookiePersistence.loadCookieHeader()
  }

  private suspend fun readRealPersistedUserAgent(): String {
    val realDataStore = createPreferencesDataStore(realDesktopPreferencesFile())
    val keyValueStorage = KeyValueStorage(realDataStore)
    return keyValueStorage.load(KeyValueStorage.KEY_HTTP_USER_AGENT).orEmpty()
  }

  private fun probeCopiedSession(
      snapshot: DesktopE2eSessionSnapshot,
      profile: DesktopE2eTestProfile,
  ): AuthProbeResult {
    val stores = createProfileStores(profile)
    seedCopiedSession(stores = stores, snapshot = snapshot)
    startAppKoin(desktopE2eTestPlatformModule(profile = profile, stores = stores))
    return try {
      val authRepository: AuthRepository = GlobalContext.get().get()
      runBlocking {
        authRepository.restorePersistedSession()
        authRepository.probeLogin()
      }
    } finally {
      stopAppKoin()
      runCatching { runBlocking { stores.cookieVault.clearAll() } }
    }
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
}

private fun seedCopiedSession(
    stores: DesktopE2eProfileStores,
    snapshot: DesktopE2eSessionSnapshot,
) {
  val cookiesStorage = FaCookiesStorage(KSafeCookiePersistence(cookieVault = stores.cookieVault))
  val userAgentStorage = UserAgentStorage(KeyValueStorage(stores.dataStore))
  runBlocking {
    cookiesStorage.saveRawCookieHeader(snapshot.cookieHeader)
    userAgentStorage.saveOverride(snapshot.userAgent)
  }
}

private fun createProfileStores(profile: DesktopE2eTestProfile): DesktopE2eProfileStores {
  ensureParentFile(profile.dataStorePath)
  ensureParentFile(profile.databasePath)
  ensureParentDir(profile.coilCacheDir)
  return DesktopE2eProfileStores(
      dataStore = createPreferencesDataStore(profile.dataStorePath),
      cookieVault = KSafe(fileName = profile.cookieVaultFileName),
  )
}

private fun realDesktopPreferencesFile(): File {
  val userHome = System.getProperty("user.home").orEmpty().ifBlank { "." }
  return File(File(userHome, ".config/fa2/datastore"), "settings.preferences_pb")
}

private fun createPreferencesDataStore(file: File): DataStore<Preferences> {
  ensureParentFile(file)
  return PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
}

private fun ensureParentFile(file: File) {
  ensureParentDir(checkNotNull(file.parentFile) { "Missing parent dir for ${file.absolutePath}" })
}

private fun ensureParentDir(dir: File) {
  if (!dir.exists()) {
    check(dir.mkdirs()) { "Unable to create directory: ${dir.absolutePath}" }
  }
  check(dir.isDirectory) { "Invalid directory: ${dir.absolutePath}" }
}

internal const val desktopE2eStartupTimeoutMs: Long = startupTimeoutMs
