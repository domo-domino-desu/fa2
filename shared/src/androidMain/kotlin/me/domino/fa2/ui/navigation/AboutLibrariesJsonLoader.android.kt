package me.domino.fa2.ui.navigation

import android.content.Context
import org.koin.core.context.GlobalContext

private const val aboutLibrariesAssetName = "aboutlibraries.json"

internal actual suspend fun loadPlatformAboutLibrariesJsonOrNull(): String? {
    val appContext = runCatching {
        GlobalContext.get().get<Context>()
    }.getOrNull() ?: return null

    return runCatching {
        appContext.assets.open(aboutLibrariesAssetName).bufferedReader().use { it.readText() }
    }.getOrNull()
}
