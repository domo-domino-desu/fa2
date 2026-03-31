package me.domino.fa2.util

import kotlin.test.Test
import kotlin.test.assertEquals
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.DownloadFileNameMode
import me.domino.fa2.data.settings.DownloadSubfolderMode
import me.domino.fa2.ui.components.platform.PlatformDownloadRequest
import me.domino.fa2.ui.components.platform.buildDownloadFileBaseName
import me.domino.fa2.ui.components.platform.composeFileName
import me.domino.fa2.ui.components.platform.normalizeTemplateResult
import me.domino.fa2.ui.components.platform.resolveDownloadFileExtension
import me.domino.fa2.ui.components.platform.resolveDownloadRelativeDirectories

class PlatformDownloadSupportTest {
  private val request =
      PlatformDownloadRequest(
          downloadUrl = "https://d.furaffinity.net/art/a/123/test-image",
          submissionId = 456,
          title = "Title",
          username = "alice",
          category = "Artwork",
          rating = "General",
          type = "Digital",
          species = "Fox",
          downloadFileNameHint = null,
      )

  @Test
  fun buildDownloadFileBaseNameUsesPresetMode() {
    val idTitleSettings = AppSettings(downloadFileNameMode = DownloadFileNameMode.ID_TITLE)
    val usernameIdSettings = AppSettings(downloadFileNameMode = DownloadFileNameMode.USERNAME_ID)
    val usernameIdTitleSettings =
        AppSettings(downloadFileNameMode = DownloadFileNameMode.USERNAME_ID_TITLE)

    assertEquals("456-Title", buildDownloadFileBaseName(idTitleSettings, request))
    assertEquals("alice-456", buildDownloadFileBaseName(usernameIdSettings, request))
    assertEquals("alice-456-Title", buildDownloadFileBaseName(usernameIdTitleSettings, request))
  }

  @Test
  fun customTemplateKeepsUnknownVariablesAndCleansEmptySegments() {
    val settings =
        AppSettings(
            downloadFileNameMode = DownloadFileNameMode.CUSTOM,
            downloadCustomFileNameTemplate = "{username}-{unknown}-{title}-{submission_id}",
        )
    val emptyTitleRequest = request.copy(title = "")
    assertEquals(
        "alice-{unknown}-456",
        buildDownloadFileBaseName(settings, emptyTitleRequest),
    )
  }

  @Test
  fun customTemplateUsesRawMetadataAndSanitizesInvalidCharacters() {
    val settings =
        AppSettings(
            downloadFileNameMode = DownloadFileNameMode.CUSTOM,
            downloadCustomFileNameTemplate = "{category}-{rating}-{type}-{species}",
        )
    val metadataRequest =
        request.copy(
            category = "Art/Stuff",
            rating = "General*",
            type = "Digital?",
            species = "Fox:Canine",
        )

    assertEquals(
        "Art_Stuff-General_-Digital_-Fox_Canine",
        buildDownloadFileBaseName(settings, metadataRequest),
    )
  }

  @Test
  fun resolveDownloadRelativeDirectoriesRespectsUsernameMode() {
    val settings = AppSettings(downloadSubfolderMode = DownloadSubfolderMode.BY_USERNAME)
    val segments = resolveDownloadRelativeDirectories(settings, request.copy(username = "a/b:c"))
    assertEquals(listOf("a_b_c"), segments)
  }

  @Test
  fun resolveDownloadFileExtensionFollowsPriority() {
    assertEquals(
        "jpg",
        resolveDownloadFileExtension(
            fileNameHint = "picture.jpg",
            downloadUrl = request.downloadUrl,
            contentType = "image/png",
        ),
    )
    assertEquals(
        "png",
        resolveDownloadFileExtension(
            fileNameHint = null,
            downloadUrl = "https://example.com/download?filename=file.png",
            contentType = "image/jpeg",
        ),
    )
    assertEquals(
        "webp",
        resolveDownloadFileExtension(
            fileNameHint = null,
            downloadUrl = "https://example.com/download",
            contentType = "image/webp; charset=utf-8",
        ),
    )
    assertEquals(
        "bin",
        resolveDownloadFileExtension(
            fileNameHint = null,
            downloadUrl = "https://example.com/download",
            contentType = null,
        ),
    )
  }

  @Test
  fun normalizeTemplateResultCollapsesDuplicateDelimiters() {
    assertEquals("alice-123", normalizeTemplateResult("alice--123"))
    assertEquals("alice_123", normalizeTemplateResult("alice___123"))
  }

  @Test
  fun composeFileNameBuildsExtensionSuffix() {
    assertEquals("sample.jpg", composeFileName("sample", "jpg"))
  }
}
