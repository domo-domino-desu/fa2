import io.github.kingsword09.symbolcraft.model.SymbolFill
import io.github.kingsword09.symbolcraft.model.SymbolVariant

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.aboutLibraries)
  alias(libs.plugins.ksp)
  alias(libs.plugins.symbolCraft)
}

kotlin {
  android {
    namespace = "me.domino.fa2.shared"
    compileSdk = 36
    minSdk = 29
    androidResources { enable = true }
  }

  jvm("desktop")

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(libs.aboutlibraries.compose.m3)
        implementation(libs.aboutlibraries.core)
        implementation(libs.coil.compose)
        implementation(libs.coil.network.ktor3)
        implementation(libs.compose.components.resources)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.native.webview)
        implementation(libs.compose.runtime)
        implementation(libs.compose.ui)
        implementation(libs.datastore.preferences.core)
        implementation(libs.htmlconverter.compose)
        implementation(libs.kermit)
        implementation(libs.koin.compose)
        implementation(libs.koin.core)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ksafe)
        implementation(libs.ksoup)
        implementation(libs.ktor.client.core)
        implementation(libs.materialyou)
        implementation(libs.room.runtime)
        implementation(libs.store5)
        implementation(libs.voyager.core)
        implementation(libs.voyager.koin)
        implementation(libs.voyager.navigator)
        implementation(libs.voyager.screenmodel)
        implementation(libs.zoomimage.compose.coil3)
      }
    }

    val jvmSharedMain by creating { dependsOn(commonMain) }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.koin.core)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.ktor.client.mock)
        implementation(libs.room.runtime)
        implementation(libs.sqlite.bundled)
      }
    }

    val desktopMain by getting {
      dependsOn(jvmSharedMain)
      dependencies {
        implementation(libs.ktor.client.okhttp)
        implementation(libs.pdfbox)
        implementation(libs.slf4j.simple)
      }
    }

    val androidMain by getting {
      dependsOn(jvmSharedMain)
      dependencies {
        implementation(libs.androidx.activity.compose)
        implementation(libs.coil.gif)
        implementation(libs.documentfile)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.pdfbox.android)
      }
    }
  }
}

dependencies {
  add("kspCommonMainMetadata", libs.room.compiler)
  add("kspDesktop", libs.room.compiler)
  add("kspAndroid", libs.room.compiler)
}

tasks.register("test") {
  group = "verification"
  dependsOn("desktopTest")
}

aboutLibraries {
  export { outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json") }
}

symbolCraft {
  packageName.set("me.domino.fa2.generated.symbols")
  outputDirectory.set("src/commonMain/kotlin")
  generatePreview.set(false)
  cacheEnabled.set(true)

  naming { pascalCase() }

  materialSymbols(
      "add",
      "close",
      "done",
      "download",
      "explore",
      "favorite",
      "history",
      "home",
      "info",
      "menu",
      "notifications",
      "restart_alt",
      "save",
      "search",
      "settings",
  ) {
    style(weight = 400, variant = SymbolVariant.OUTLINED, fill = SymbolFill.FILLED)
  }

  materialSymbols(
      "arrow_back",
      "arrow_circle_right",
      "arrow_downward_alt",
      "arrow_upward_alt",
      "category",
      "comment",
      "content_copy",
      "date_range",
      "document_scanner",
      "download",
      "expand_less",
      "expand_more",
      "explore",
      "file_present",
      "filter_alt",
      "home",
      "image",
      "inventory_2",
      "keyboard_arrow_down",
      "keyboard_arrow_right",
      "keyboard_arrow_up",
      "keyboard_double_arrow_down",
      "keyboard_double_arrow_up",
      "language",
      "logout",
      "mail",
      "menu",
      "movie",
      "music_note",
      "notifications",
      "refresh",
      "search",
      "share",
      "subject",
      "tag",
      "translate",
      "troubleshoot",
      "vertical_align_bottom",
      "vertical_align_top",
      "visibility",
      "wrap_text",
  ) {
    style(weight = 400, variant = SymbolVariant.OUTLINED, fill = SymbolFill.UNFILLED)
  }

  materialSymbol("favorite") {
    style(weight = 400, variant = SymbolVariant.OUTLINED, fill = SymbolFill.UNFILLED)
  }

  externalIcons(
      *listOf(
              "battle-net",
              "bluesky",
              "deviantart",
              "discord",
              "etsy",
              "facebook",
              "instagram",
              "mastodon",
              "patreon",
              "pixiv",
              "playstation",
              "reddit",
              "steam",
              "telegram",
              "tiktok",
              "tumblr",
              "twitch",
              "twitter",
              "xbox",
              "youtube",
          )
          .toTypedArray(),
      libraryName = "fontawesomebrands",
  ) {
    urlTemplate =
        "https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/brands/{name}.svg"
  }

  externalIcons(
      *listOf(
              "archiveofourown",
              "battledotnet",
              "furrynetwork",
              "kofi",
              "nintendo3ds",
              "nintendoswitch",
              "wattpad",
              "weasyl",
              "wiiu",
          )
          .toTypedArray(),
      libraryName = "simpleicons",
  ) {
    urlTemplate = "https://cdn.jsdelivr.net/npm/simple-icons@latest/icons/{name}.svg"
  }
}

val symbolCraftConsumerTasks =
    setOf(
        "compileCommonMainKotlinMetadata",
        "kspCommonMainKotlinMetadata",
        "compileAndroidMain",
        "kspAndroidMain",
        "compileKotlinDesktop",
        "kspKotlinDesktop",
        "compileDevKotlinDesktop",
        "kspDevKotlinDesktop",
    )

tasks.configureEach {
  if (name in symbolCraftConsumerTasks) {
    dependsOn("generateSymbolCraftIcons")
  }
}
