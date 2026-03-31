package me.domino.fa2.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.anifantakis.lib.ksafe.KSafe
import java.io.File
import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.data.local.AppDatabaseBuilderFactory
import me.domino.fa2.desktop.ocr.DesktopOcrBlockExtractor
import me.domino.fa2.desktop.ocr.RapidImageTextRecognitionPort
import me.domino.fa2.desktop.ocr.RapidOcrBlockExtractor
import me.domino.fa2.di.KOIN_QUALIFIER_COOKIE_VAULT
import me.domino.fa2.di.KOIN_QUALIFIER_SETTINGS_SECRET_VAULT
import me.domino.fa2.domain.ocr.ImageTextRecognitionPort
import me.domino.fa2.i18n.SystemLanguageProvider
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Desktop 平台依赖模块。 */
fun desktopPlatformModule(): Module = module {
  single<DesktopOcrBlockExtractor> { RapidOcrBlockExtractor() }
  single<ImageTextRecognitionPort> { RapidImageTextRecognitionPort(get()) }
  single<SystemLanguageProvider> {
    object : SystemLanguageProvider {
      override fun currentLanguageTag(): String = java.util.Locale.getDefault().toLanguageTag()
    }
  }
  single<AppDatabaseBuilderFactory> {
    AppDatabaseBuilderFactory {
      val dbFile = resolveDatabaseFile()
      ensureDatabaseDir(dbFile)
      Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath).setDriver(BundledSQLiteDriver())
    }
  }
  single<DataStore<Preferences>> {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = {
          val dataStoreFile = resolvePreferencesFile()
          ensureParentDir(dataStoreFile)
          dataStoreFile.absolutePath.toPath()
        }
    )
  }
  single(named(KOIN_QUALIFIER_COOKIE_VAULT)) { KSafe(fileName = KOIN_QUALIFIER_COOKIE_VAULT) }
  single(named(KOIN_QUALIFIER_SETTINGS_SECRET_VAULT)) {
    KSafe(fileName = KOIN_QUALIFIER_SETTINGS_SECRET_VAULT)
  }
}

/** 解析数据库文件路径。 */
private fun resolveDatabaseFile(): File {
  val dataDir = File(System.getProperty("user.home"), ".cache/fa2/room-cache")
  return File(dataDir, "fa2-cache.db")
}

/** 配置存储文件路径。 */
private fun resolvePreferencesFile(): File {
  val dataDir = File(System.getProperty("user.home"), ".config/fa2/datastore")
  return File(dataDir, "settings.preferences_pb")
}

/**
 * 确保数据库目录可写。
 *
 * @param dbFile 数据库文件。
 */
private fun ensureDatabaseDir(dbFile: File) {
  val parent = checkNotNull(dbFile.parentFile) { "数据库文件缺少父目录：${dbFile.absolutePath}" }
  ensureDirReady(parent)
}

/** 确保父目录可写。 */
private fun ensureParentDir(file: File) {
  val parent = checkNotNull(file.parentFile) { "文件缺少父目录：${file.absolutePath}" }
  ensureDirReady(parent)
}

/** 统一目录可用性检查。 */
private fun ensureDirReady(parent: File) {
  if (!parent.exists()) {
    check(parent.mkdirs()) { "无法创建目录：${parent.absolutePath}" }
  }
  check(parent.isDirectory) { "目录非法：${parent.absolutePath}" }
  check(parent.canWrite()) { "目录不可写：${parent.absolutePath}" }
}
