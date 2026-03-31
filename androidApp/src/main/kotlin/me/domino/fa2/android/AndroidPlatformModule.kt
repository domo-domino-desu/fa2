package me.domino.fa2.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import eu.anifantakis.lib.ksafe.KSafe
import java.io.File
import me.domino.fa2.android.ocr.MlKitImageTextRecognitionPort
import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.data.local.AppDatabaseBuilderFactory
import me.domino.fa2.di.KOIN_QUALIFIER_COOKIE_VAULT
import me.domino.fa2.di.KOIN_QUALIFIER_SETTINGS_SECRET_VAULT
import me.domino.fa2.domain.ocr.ImageTextRecognitionPort
import me.domino.fa2.i18n.SystemLanguageProvider
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Android 平台依赖模块。 */
fun androidPlatformModule(
    /** Application Context。 */
    context: Context
): Module = module {
  single<Context> { context.applicationContext }
  single<ImageTextRecognitionPort> { MlKitImageTextRecognitionPort() }
  single<SystemLanguageProvider> {
    object : SystemLanguageProvider {
      override fun currentLanguageTag(): String = java.util.Locale.getDefault().toLanguageTag()
    }
  }

  single<AppDatabaseBuilderFactory> {
    val appContext = context.applicationContext
    val cacheDbFile =
        File(appContext.cacheDir, "room-cache/fa2-cache.db").apply { parentFile?.mkdirs() }
    AppDatabaseBuilderFactory {
      Room.databaseBuilder(
          context = appContext,
          klass = AppDatabase::class.java,
          name = cacheDbFile.absolutePath,
      )
    }
  }
  single<DataStore<Preferences>> {
    val appContext = context.applicationContext
    PreferenceDataStoreFactory.createWithPath(
        produceFile = {
          File(appContext.filesDir, "datastore/fa2.preferences_pb")
              .apply { parentFile?.mkdirs() }
              .absolutePath
              .toPath()
        }
    )
  }
  single(named(KOIN_QUALIFIER_COOKIE_VAULT)) {
    KSafe(context = context.applicationContext, fileName = KOIN_QUALIFIER_COOKIE_VAULT)
  }
  single(named(KOIN_QUALIFIER_SETTINGS_SECRET_VAULT)) {
    KSafe(context = context.applicationContext, fileName = KOIN_QUALIFIER_SETTINGS_SECRET_VAULT)
  }
}
