import org.gradle.api.tasks.Copy

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
}

android {
  namespace = "me.domino.fa2.android"
  compileSdk = 36
  buildToolsVersion = "36.0.0"

  val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
  val appVersionCode = providers.gradleProperty("APP_VERSION_CODE").map(String::toInt).get()

  val signingStoreFilePath =
    providers
      .gradleProperty("ANDROID_SIGNING_STORE_FILE")
      .orElse(rootProject.file("dummy.keystore").absolutePath)
      .get()
  val signingStorePassword =
    providers.gradleProperty("ANDROID_SIGNING_STORE_PASSWORD").orElse("123456").get()
  val signingKeyAlias = providers.gradleProperty("ANDROID_SIGNING_KEY_ALIAS").orElse("dummy").get()
  val signingKeyPassword =
    providers.gradleProperty("ANDROID_SIGNING_KEY_PASSWORD").orElse("123456").get()

  defaultConfig {
    applicationId = "me.domino.fa2.android"
    minSdk = 24
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      storeFile = file(signingStoreFilePath)
      storePassword = signingStorePassword
      keyAlias = signingKeyAlias
      keyPassword = signingKeyPassword
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  sourceSets {
    getByName("main") {
      assets.directories.add(
        layout.buildDirectory.dir("generated/aboutlibrariesAssets").get().asFile.path
      )
    }
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":shared"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.compose.material3)
  implementation(libs.compose.foundation)
  implementation(libs.google.material)
  implementation(libs.coil.compose)
  implementation(libs.coil.gif)
  implementation(libs.datastore.preferences.core)
  implementation(libs.ksafe)
  implementation(libs.koin.core)
  implementation(libs.kermit)
  implementation(libs.room.runtime)
  androidTestImplementation(libs.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
}

val copyAboutLibrariesJsonToAssets by
  tasks.registering(Copy::class) {
    from(
      project(":shared")
        .layout
        .projectDirectory
        .file("src/commonMain/composeResources/files/aboutlibraries.json")
    )
    into(layout.buildDirectory.dir("generated/aboutlibrariesAssets"))
  }

tasks.named("preBuild").configure {
  dependsOn(copyAboutLibrariesJsonToAssets)
  dependsOn(rootProject.tasks.named("generateAppIcons"))
}
