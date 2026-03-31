package me.domino.fa2.ui.pages.submission

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.anifantakis.lib.ksafe.KSafe
import kotlin.random.Random
import me.domino.fa2.application.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.AppSettingsStorage
import me.domino.fa2.domain.translation.TranslationPort
import me.domino.fa2.domain.translation.TranslationRequest
import me.domino.fa2.i18n.SystemLanguageProvider
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
  val settingsService =
      AppSettingsService(
          AppSettingsStorage(
              kv = keyValueStorage,
              secretVault = KSafe(fileName = "fa2_submission_settings_$randomSuffix"),
          )
      )
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

internal fun createSubmissionScreenModelForTest(
    initialSid: Int,
    items: List<SubmissionThumbnail>,
    submissionSource: SubmissionPagerDetailSource,
    translationService: SubmissionDescriptionTranslationService,
    settingsService: AppSettingsService? = null,
    systemLanguageProvider: SystemLanguageProvider? = null,
    contextId: String = "test-context:$initialSid",
): SubmissionScreenModel {
  val contextScreenModel = SubmissionContextScreenModel()
  contextScreenModel.ensureSeedContext(
      contextId = contextId,
      sourceKind = SubmissionContextSourceKind.SEQUENCE,
      items = items,
      selectedSid = initialSid,
  )
  return SubmissionScreenModel(
      initialSid = initialSid,
      contextId = contextId,
      contextScreenModel = contextScreenModel,
      submissionSource = submissionSource,
      translationService = translationService,
      settingsService = settingsService,
      systemLanguageProvider = systemLanguageProvider,
  )
}

internal fun createSubmissionScreenModelForTest(
    initialSid: Int,
    adapter: SubmissionSourceAdapter,
    initialPage: SubmissionLoadedPage,
    submissionSource: SubmissionPagerDetailSource,
    translationService: SubmissionDescriptionTranslationService,
    settingsService: AppSettingsService? = null,
    systemLanguageProvider: SystemLanguageProvider? = null,
    contextId: String = "test-context:$initialSid",
): SubmissionScreenModel {
  val contextScreenModel = SubmissionContextScreenModel()
  contextScreenModel.ensureSeedContext(
      contextId = contextId,
      adapter = adapter,
      initialPage = initialPage,
      selectedSid = initialSid,
  )
  return SubmissionScreenModel(
      initialSid = initialSid,
      contextId = contextId,
      contextScreenModel = contextScreenModel,
      submissionSource = submissionSource,
      translationService = translationService,
      settingsService = settingsService,
      systemLanguageProvider = systemLanguageProvider,
  )
}
