package me.domino.fa2.ui.pages.submission

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlin.random.Random
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.AppSettingsStorage
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.data.translation.TranslationPort
import me.domino.fa2.data.translation.TranslationRequest
import okio.FileSystem
import okio.Path.Companion.toPath

internal suspend fun createTestSubmissionTranslationService(
    translate: suspend (TranslationRequest) -> String = { request -> request.sourceText }
): SubmissionDescriptionTranslationService {
  val randomSuffix = Random.nextLong().toString().replace('-', '0')
  val tempPath =
      "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-submission-screen-$randomSuffix.preferences_pb"
          .toPath()
  val keyValueStorage =
      KeyValueStorage(PreferenceDataStoreFactory.createWithPath(produceFile = { tempPath }))
  val settingsService = AppSettingsService(AppSettingsStorage(keyValueStorage))
  val translationPort =
      object : TranslationPort {
        override suspend fun translate(request: TranslationRequest): String = translate(request)
      }
  settingsService.ensureLoaded()

  return SubmissionDescriptionTranslationService(
      translationPort = translationPort,
      settingsService = settingsService,
  )
}
