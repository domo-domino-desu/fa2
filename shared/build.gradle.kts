plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.aboutLibraries)
  alias(libs.plugins.ksp)
  alias(libs.plugins.ktfmt)
}

kotlin {
  android {
    namespace = "me.domino.fa2.shared"
    compileSdk = 36
    minSdk = 24
  }

  jvm("desktop")

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.material.icons.extended)
        implementation(libs.compose.ui)
        implementation(libs.compose.components.resources)
        implementation(libs.compose.native.webview)
        implementation(libs.ktor.client.core)
        implementation(libs.ksoup)
        implementation(libs.ksafe)
        implementation(libs.koin.core)
        implementation(libs.koin.compose)
        implementation(libs.voyager.core)
        implementation(libs.voyager.navigator)
        implementation(libs.voyager.screenmodel)
        implementation(libs.voyager.koin)
        implementation(libs.coil.compose)
        implementation(libs.coil.network.ktor3)
        implementation(libs.zoomimage.compose.coil3)
        implementation(libs.htmlconverter.compose)
        implementation(libs.materialyou)
        implementation(libs.aboutlibraries.core)
        implementation(libs.aboutlibraries.compose.m3)
        implementation(libs.datastore.preferences.core)
        implementation(libs.room.runtime)
        implementation(libs.store5)
        implementation(libs.kermit)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.koin.core)
        implementation(libs.room.runtime)
        implementation(libs.sqlite.bundled)
      }
    }

    val desktopMain by getting {
      dependencies {
        implementation(libs.ktor.client.okhttp)
        implementation(libs.slf4j.simple)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.androidx.activity.compose)
        implementation(libs.coil.gif)
        implementation(libs.ktor.client.okhttp)
      }
    }
  }
}

dependencies {
  add("kspCommonMainMetadata", libs.room.compiler)
  add("kspDesktop", libs.room.compiler)
  add("kspAndroid", libs.room.compiler)
}

ktfmt { googleStyle() }

tasks.register("test") {
  group = "verification"
  dependsOn("desktopTest")
}

aboutLibraries {
  export { outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json") }
}
