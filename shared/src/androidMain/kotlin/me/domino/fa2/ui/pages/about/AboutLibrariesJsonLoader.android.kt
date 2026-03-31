package me.domino.fa2.ui.pages.about

private const val aboutLibrariesComposeAssetPath =
    "composeResources/fa2.shared.generated.resources/files/aboutlibraries.json"

internal actual suspend fun loadPlatformAboutLibrariesJsonOrNull(): String? {
  val appContext = AboutLibrariesAndroidContextHolder.context ?: return null

  return runCatching {
        appContext.assets.open(aboutLibrariesComposeAssetPath).bufferedReader().use {
          it.readText()
        }
      }
      .getOrNull()
}
