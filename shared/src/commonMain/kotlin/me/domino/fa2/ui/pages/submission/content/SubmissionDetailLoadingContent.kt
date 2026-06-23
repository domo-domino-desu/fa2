package me.domino.fa2.ui.pages.submission.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.ui.components.media.ThumbnailImage
import me.domino.fa2.ui.components.state.SkeletonBlock
import me.domino.fa2.ui.pages.submission.attachmenttext.*
import me.domino.fa2.ui.pages.submission.imageocr.*
import me.domino.fa2.ui.pages.submission.pager.*
import me.domino.fa2.ui.pages.submission.series.*
import me.domino.fa2.ui.pages.submission.translation.*
import me.domino.fa2.ui.utils.sanitizeDetailAspectRatio

@Composable
internal fun SubmissionDetailLoadingContent(
    item: SubmissionThumbnail,
    isBlockedByTag: Boolean,
    blockedSubmissionMode: BlockedSubmissionPagerMode,
    isBlockedMediaRevealed: Boolean,
) {
  val shouldBlurBlockedMedia =
      isBlockedByTag &&
          blockedSubmissionMode == BlockedSubmissionPagerMode.BLUR_THEN_OPEN &&
          !isBlockedMediaRevealed
  val mediaAspectRatio = sanitizeDetailAspectRatio(item.thumbnailAspectRatio)
  Column(
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(mediaAspectRatio)
    ) {
      if (item.thumbnailUrl.isBlank()) {
        SkeletonBlock(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
      } else {
        ThumbnailImage(
            url = item.thumbnailUrl,
            modifier =
                Modifier.fillMaxSize()
                    .then(if (shouldBlurBlockedMedia) Modifier.blur(26.dp) else Modifier),
            showLoadingPlaceholder = false,
        )
      }
    }
    SkeletonBlock(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(92.dp),
    )
    SkeletonBlock(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp),
    )
    SkeletonBlock(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(180.dp),
    )
  }
}
