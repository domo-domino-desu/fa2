package me.domino.fa2.ui.pages.submission

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.anifantakis.lib.ksafe.KSafe
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.local.settings.AppSettingsStorage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.remote.translation.SubmissionTextTranslationEngine
import me.domino.fa2.data.remote.translation.TranslationPort
import me.domino.fa2.data.remote.translation.TranslationRequest
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.domain.ocr.ImageOcrResult
import me.domino.fa2.domain.ocr.ImageTextRecognitionPort
import me.domino.fa2.domain.submissionviewer.api.SubmissionContextSourceKind
import me.domino.fa2.domain.submissionviewer.api.SubmissionPageSnapshot
import me.domino.fa2.domain.submissionviewer.api.SubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.pager.SubmissionContextScreenModel
import me.domino.fa2.ui.pages.submission.pager.SubmissionDetailSource
import me.domino.fa2.ui.pages.submission.pager.SubmissionScreenModel
import okio.FileSystem
import okio.Path.Companion.toPath

internal suspend fun createTestSubmissionTranslationService(
    settingsService: AppSettingsService? = null,
    translate: suspend (TranslationRequest) -> String = { request -> request.sourceText },
): SubmissionTextTranslationEngine {
  val resolvedSettingsService = settingsService ?: createTestAppSettingsService()
  val translationPort = createTestTranslationPort(translate)
  return SubmissionTextTranslationEngine(translationPort, resolvedSettingsService)
}

internal suspend fun createTestSubmissionImageOcrTranslationService(
    settingsService: AppSettingsService? = null,
    translate: suspend (TranslationRequest) -> String = { request -> request.sourceText },
): SubmissionTextTranslationEngine {
  val resolvedSettingsService = settingsService ?: createTestAppSettingsService()
  val translationPort = createTestTranslationPort(translate)
  return SubmissionTextTranslationEngine(translationPort, resolvedSettingsService)
}

internal suspend fun createTestAppSettingsService(): AppSettingsService {
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
  settingsService.ensureLoaded()
  return settingsService
}

internal fun createTestTranslationPort(
    translate: suspend (TranslationRequest) -> String
): TranslationPort =
    object : TranslationPort {
      override suspend fun translate(request: TranslationRequest): String = translate(request)
    }

internal interface SubmissionPagerDetailSource {
  suspend fun loadBySid(sid: Int): ResourceResult<Submission>

  suspend fun loadByUrl(url: String): ResourceResult<Submission>

  suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit = {},
  ): ResourceResult<AttachmentTextDocument>

  suspend fun toggleFavorite(sid: Int, actionUrl: String): ResourceResult<Unit>

  suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): ResourceResult<Unit>
}

private fun SubmissionPagerDetailSource.toProductionSource() =
    SubmissionDetailSource(
        loadBySid = ::loadBySid,
        loadByUrl = ::loadByUrl,
        loadAttachmentText = ::loadAttachmentText,
        toggleFavorite = ::toggleFavorite,
        blockTag = ::blockTag,
    )

internal data class TestImageOcr(
    val bytes: suspend (String) -> ByteArray,
    val recognition: ImageTextRecognitionPort,
)

internal fun createTestSubmissionImageOcrService(
    recognize: suspend (String) -> ImageOcrResult = { ImageOcrResult(emptyList()) }
): TestImageOcr {
  var url = ""
  return TestImageOcr(
      bytes = { imageUrl ->
        url = imageUrl
        byteArrayOf()
      },
      recognition =
          object : ImageTextRecognitionPort {
            override suspend fun recognize(imageBytes: ByteArray): ImageOcrResult = recognize(url)
          },
  )
}

internal fun createSubmissionScreenModelForTest(
    initialSid: Int,
    items: List<SubmissionThumbnail>,
    submissionSource: SubmissionPagerDetailSource,
    translationService: SubmissionTextTranslationEngine,
    imageOcrService: TestImageOcr = createTestSubmissionImageOcrService(),
    imageOcrTranslationService: SubmissionTextTranslationEngine = runBlocking {
      createTestSubmissionImageOcrTranslationService()
    },
    settingsService: AppSettingsService? = null,
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
      submissionSource = submissionSource.toProductionSource(),
      translationService = translationService,
      loadImageBytes = imageOcrService.bytes,
      imageRecognition = imageOcrService.recognition,
      imageOcrTranslationService = imageOcrTranslationService,
      settingsService = settingsService,
  )
}

internal fun createSubmissionScreenModelForTest(
    initialSid: Int,
    adapter: SubmissionSourceAdapter,
    initialPage: SubmissionPageSnapshot,
    submissionSource: SubmissionPagerDetailSource,
    translationService: SubmissionTextTranslationEngine,
    imageOcrService: TestImageOcr = createTestSubmissionImageOcrService(),
    imageOcrTranslationService: SubmissionTextTranslationEngine = runBlocking {
      createTestSubmissionImageOcrTranslationService()
    },
    settingsService: AppSettingsService? = null,
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
      submissionSource = submissionSource.toProductionSource(),
      translationService = translationService,
      loadImageBytes = imageOcrService.bytes,
      imageRecognition = imageOcrService.recognition,
      imageOcrTranslationService = imageOcrTranslationService,
      settingsService = settingsService,
  )
}
