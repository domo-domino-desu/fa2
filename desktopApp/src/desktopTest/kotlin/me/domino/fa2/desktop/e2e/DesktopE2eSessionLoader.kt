package me.domino.fa2.desktop.e2e

import me.domino.fa2.data.model.AuthProbeResult
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.repository.AuthRepository
import me.domino.fa2.di.startAppKoin
import me.domino.fa2.di.stopAppKoin

internal object DesktopE2eSessionLoader {
  fun preflight(): DesktopE2ePreflightResult {
    val originalHome = System.getProperty("user.home").orEmpty().ifBlank { "." }
    val realCookieHeader = DesktopE2eSessionStorage.readRealCookieHeaderBlocking().trim()
    if (realCookieHeader.isBlank()) {
      return DesktopE2ePreflightResult.Fail(
          "No persisted desktop session cookie was found in $originalHome."
      )
    }

    val persistedUserAgent = DesktopE2eSessionStorage.readRealPersistedUserAgentBlocking()
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

  private fun probeCopiedSession(
      snapshot: DesktopE2eSessionSnapshot,
      profile: DesktopE2eTestProfile,
  ): AuthProbeResult {
    val stores = createProfileStores(profile)
    DesktopE2eSessionStorage.seedProfileBlocking(stores = stores, snapshot = snapshot)
    val koin = startAppKoin(desktopE2eTestPlatformModule(profile = profile, stores = stores))
    return try {
      val authRepository = koin.get<AuthRepository>()
      kotlinx.coroutines.runBlocking {
        authRepository.restorePersistedSession()
        authRepository.probeLogin()
      }
    } finally {
      stopAppKoin()
      runCatching { DesktopE2eSessionStorage.clearCookieVaultBlocking(stores.cookieVault) }
    }
  }
}
