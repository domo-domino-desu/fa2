package me.domino.fa2.ui.pages.about

private const val aboutLibrariesAssetName = "aboutlibraries.json"

internal actual suspend fun loadPlatformAboutLibrariesJsonOrNull(): String? {
  val appContext = AboutLibrariesAndroidContextHolder.context ?: return null

  return runCatching {
        appContext.assets.open(aboutLibrariesAssetName).bufferedReader().use { it.readText() }
      }
      .getOrNull()
}
