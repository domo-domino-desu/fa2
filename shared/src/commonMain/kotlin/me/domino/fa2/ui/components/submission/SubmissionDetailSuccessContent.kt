package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.util.ParserUtils
import me.domino.fa2.util.sanitizeDetailAspectRatio
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubmissionDetailSuccessContent(
    item: SubmissionThumbnail,
    detail: Submission,
    blockedKeywords: Set<String>,
    favoriteErrorMessage: String?,
    onOpenAuthor: (String) -> Unit,
    onSearchKeyword: (String) -> Unit,
    onKeywordLongPress: (String) -> Unit,
    onOpenBrowseFilter: (category: Int, type: Int, species: Int) -> Unit,
    onCopySubmissionUrl: (String) -> Unit,
    onOpenImageZoom: (String) -> Unit,
    isBlockedByTag: Boolean,
    blockedSubmissionMode: BlockedSubmissionPagerMode,
    isBlockedMediaRevealed: Boolean,
    onRevealBlockedMedia: () -> Unit,
    descriptionTranslationService: SubmissionDescriptionTranslationService,
    requestPagerFocus: () -> Unit,
) {
  val taxonomyRepository = koinInject<FaTaxonomyRepository>()
  val taxonomyCatalog by taxonomyRepository.catalog.collectAsState()
  val metrics =
      remember(detail) {
        listOf(
            SubmissionInfoMetric(
                icon = FaMaterialSymbols.Filled.Visibility,
                text = detail.viewCount.toString(),
            ),
            SubmissionInfoMetric(
                icon = FaMaterialSymbols.AutoMirrored.Filled.Comment,
                text = detail.commentCount.toString(),
            ),
            SubmissionInfoMetric(
                icon = FaMaterialSymbols.Filled.Favorite,
                text = detail.favoriteCount.toString(),
            ),
            SubmissionInfoMetric(
                icon = FaMaterialSymbols.Filled.Tag,
                text = "ID ${detail.id}",
                onClick = { onCopySubmissionUrl(detail.submissionUrl) },
            ),
            SubmissionInfoMetric(icon = FaMaterialSymbols.Filled.Image, text = detail.size),
            SubmissionInfoMetric(icon = FaMaterialSymbols.Filled.Download, text = detail.fileSize),
        )
      }

  val derivedThumbnailUrl =
      ParserUtils.deriveSubmissionThumbnailUrlFromFullImage(
              sid = item.id,
              fullImageUrl = detail.fullImageUrl,
          )
          .orEmpty()
  val thumbnailUrl = item.thumbnailUrl.ifBlank { derivedThumbnailUrl }
  val mediaUrl = detail.fullImageUrl.ifBlank { detail.previewImageUrl }.ifBlank { thumbnailUrl }
  val zoomImageUrl = mediaUrl.trim()
  val browseFilter =
      remember(detail.category, detail.type, detail.species, taxonomyCatalog) {
        SubmissionBrowseFilter(
            category = taxonomyRepository.findCategoryIdByEnglishLabel(detail.category),
            type = taxonomyRepository.findTypeIdByEnglishLabel(detail.type),
            species = taxonomyRepository.findSpeciesIdByEnglishLabel(detail.species),
        )
      }
  val localizedCategory =
      remember(detail.category, taxonomyCatalog) {
        taxonomyRepository.categoryDisplayNameByEnglishLabel(detail.category) ?: detail.category
      }
  val localizedType =
      remember(detail.type, taxonomyCatalog) {
        taxonomyRepository.typeDisplayNameByEnglishLabel(detail.type) ?: detail.type
      }
  val localizedSpecies =
      remember(detail.species, taxonomyCatalog) {
        taxonomyRepository.speciesDisplayNameByEnglishLabel(detail.species) ?: detail.species
      }
  val keywordChips =
      remember(detail) { detail.keywords.filter { chip -> chip.isNotBlank() }.distinct().take(12) }
  val shouldBlurBlockedMedia =
      isBlockedByTag &&
          blockedSubmissionMode == BlockedSubmissionPagerMode.BLUR_THEN_OPEN &&
          !isBlockedMediaRevealed
  val detailMediaInteractionSource = remember { MutableInteractionSource() }

  Column(
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(sanitizeDetailAspectRatio(detail.aspectRatio))
                .then(
                    if (shouldBlurBlockedMedia || zoomImageUrl.isNotBlank()) {
                      Modifier.clickable(
                          interactionSource = detailMediaInteractionSource,
                          indication = null,
                      ) {
                        if (shouldBlurBlockedMedia) {
                          onRevealBlockedMedia()
                        } else if (zoomImageUrl.isNotBlank()) {
                          onOpenImageZoom(zoomImageUrl)
                        }
                      }
                    } else {
                      Modifier
                    }
                )
    ) {
      NetworkImage(
          url = mediaUrl,
          modifier =
              Modifier.fillMaxSize()
                  .then(if (shouldBlurBlockedMedia) Modifier.blur(26.dp) else Modifier),
          thumbnailUrl = thumbnailUrl,
          showLoadingPlaceholder = false,
      )
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = detail.title,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
      )
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        metrics.forEach { metric -> SubmissionInfoMetricChip(metric = metric) }
      }
    }
    SubmissionAuthorRow(
        authorDisplayName = detail.authorDisplayName.ifBlank { detail.author },
        author = detail.author,
        authorAvatarUrl = detail.authorAvatarUrl.ifBlank { item.authorAvatarUrl },
        timestamp = detail.timestampNatural,
        onOpenAuthor = onOpenAuthor,
    )
    SubmissionBrowseMetadataSection(
        rating = detail.rating,
        category = localizedCategory,
        type = localizedType,
        species = localizedSpecies,
        browseFilter = browseFilter,
        onOpenBrowseFilter = onOpenBrowseFilter,
    )
    SubmissionKeywordsSection(
        keywordChips = keywordChips,
        blockedKeywords = blockedKeywords,
        onSearchKeyword = onSearchKeyword,
        onKeywordLongPress = onKeywordLongPress,
    )
    SubmissionDescriptionCard(
        descriptionHtml = detail.descriptionHtml,
        translationService = descriptionTranslationService,
        requestPagerFocus = requestPagerFocus,
    )
    SubmissionCommentsCard(
        commentCount = detail.commentCount,
        comments = detail.comments,
        onOpenAuthor = onOpenAuthor,
    )
    if (!favoriteErrorMessage.isNullOrBlank()) {
      Text(
          text = favoriteErrorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(horizontal = 16.dp),
      )
    }
  }
}
