package me.domino.fa2.application.translation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.anifantakis.lib.ksafe.KSafe
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.AppSettingsStorage
import me.domino.fa2.domain.translation.TranslationPort
import me.domino.fa2.domain.translation.TranslationRequest
import okio.FileSystem
import okio.Path.Companion.toPath

class SubmissionImageOcrTranslationServiceTest {
  @Test
  fun translatesMultipleTextsInOrder() = runTest {
    val service = createService { request -> request.sourceText.uppercase() }

    val results = service.translateTexts(listOf("hello", "world"))

    assertEquals(2, results.size)
    assertEquals(
        "HELLO",
        assertIs<SubmissionImageOcrTranslationResult.Success>(results[0]).translatedText,
    )
    assertEquals(
        "WORLD",
        assertIs<SubmissionImageOcrTranslationResult.Success>(results[1]).translatedText,
    )
  }

  @Test
  fun returnsEmptyResultForEmptyTranslation() = runTest {
    val service = createService { "" }

    val result = service.translateText("hello")

    assertEquals(SubmissionImageOcrTranslationResult.Empty, result)
  }

  @Test
  fun translatesSingleEditedBlock() = runTest {
    val service = createService { request -> "translated:${request.sourceText}" }

    val result = service.translateText("edited")

    assertEquals(
        "translated:edited",
        assertIs<SubmissionImageOcrTranslationResult.Success>(result).translatedText,
    )
  }
}

private fun createService(
    translate: suspend (TranslationRequest) -> String,
): SubmissionImageOcrTranslationService = runBlocking {
  val randomSuffix = Random.nextLong().toString().replace('-', '0')
  val tempPath =
      "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-image-ocr-translation-$randomSuffix.preferences_pb"
          .toPath()
  val keyValueStorage =
      KeyValueStorage(PreferenceDataStoreFactory.createWithPath(produceFile = { tempPath }))
  val settingsService =
      AppSettingsService(
          AppSettingsStorage(
              kv = keyValueStorage,
              secretVault = KSafe(fileName = "fa2_image_ocr_translation_$randomSuffix"),
          )
      )
  settingsService.ensureLoaded()
  SubmissionImageOcrTranslationService(
      translationPort =
          object : TranslationPort {
            override suspend fun translate(request: TranslationRequest): String = translate(request)
          },
      settingsService = settingsService,
  )
}
