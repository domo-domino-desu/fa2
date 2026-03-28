package me.domino.fa2.ui.pages.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import me.domino.fa2.data.model.GalleryFolder
import me.domino.fa2.data.model.GalleryFolderGroup
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.ui.pages.user.gallery.UserSubmissionSectionUiState
import me.domino.fa2.ui.pages.user.gallery.prepareForFolderSwitch
import me.domino.fa2.ui.pages.user.gallery.withActiveFolder

class UserSubmissionSectionScreenModelTest {
  @Test
  fun prepareForFolderSwitchPreservesFolderGroupsAndClearsSubmissions() {
    val originalGroups =
        listOf(
            GalleryFolderGroup(
                title = "Folders",
                folders =
                    listOf(
                        GalleryFolder(title = "A", url = "/folder/a", isActive = true),
                        GalleryFolder(title = "B", url = "/folder/b", isActive = false),
                    ),
            )
        )
    val originalState =
        UserSubmissionSectionUiState(
            submissions = listOf(fakeSubmissionThumbnail(1)),
            nextPageUrl = "/page/2",
            folderGroups = originalGroups,
        )

    val updated = originalState.prepareForFolderSwitch("/folder/b")

    assertEquals(emptyList(), updated.submissions)
    assertEquals(null, updated.nextPageUrl)
    assertEquals("/folder/b", updated.folderGroups.single().folders.single { it.isActive }.url)
  }

  @Test
  fun withActiveFolderReturnsOriginalListWhenActiveFolderIsUnchanged() {
    val originalGroups =
        listOf(
            GalleryFolderGroup(
                folders =
                    listOf(
                        GalleryFolder(title = "A", url = "/folder/a", isActive = true),
                        GalleryFolder(title = "B", url = "/folder/b", isActive = false),
                    ),
            )
        )

    val updated = originalGroups.withActiveFolder("/folder/a")

    assertSame(originalGroups, updated)
  }
}

private fun fakeSubmissionThumbnail(id: Int) =
    SubmissionThumbnail(
        id = id,
        submissionUrl = "/view/$id",
        title = "Title $id",
        author = "author",
        authorAvatarUrl = "",
        thumbnailUrl = "",
        thumbnailAspectRatio = 1f,
        categoryTag = "c_artwork_digital",
    )
