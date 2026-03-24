package me.domino.fa2.ui.component.submission

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.component.FaBrowseTaxonomyOptions
import me.domino.fa2.ui.component.NetworkImage
import me.domino.fa2.util.ParserUtils
import me.domino.fa2.util.sanitizeDetailAspectRatio

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
    onOpenImageZoom: (String) -> Unit,
    isBlockedByTag: Boolean,
    blockedSubmissionMode: BlockedSubmissionPagerMode,
    isBlockedMediaRevealed: Boolean,
    onRevealBlockedMedia: () -> Unit,
    descriptionTranslationService: SubmissionDescriptionTranslationService,
    requestPagerFocus: () -> Unit,
) {
    val metrics = remember(detail) {
        listOf(
            SubmissionInfoMetric(icon = Icons.Filled.Visibility, text = detail.viewCount.toString()),
            SubmissionInfoMetric(icon = Icons.AutoMirrored.Filled.Comment, text = detail.commentCount.toString()),
            SubmissionInfoMetric(icon = Icons.Filled.Favorite, text = detail.favoriteCount.toString()),
            SubmissionInfoMetric(icon = Icons.Filled.Tag, text = "ID ${detail.id}"),
            SubmissionInfoMetric(icon = Icons.Filled.Image, text = detail.size),
            SubmissionInfoMetric(icon = Icons.Filled.Image, text = detail.fileSize),
        )
    }

    val derivedThumbnailUrl = ParserUtils.deriveSubmissionThumbnailUrlFromFullImage(
        sid = item.id,
        fullImageUrl = detail.fullImageUrl,
    ).orEmpty()
    val thumbnailUrl = item.thumbnailUrl.ifBlank { derivedThumbnailUrl }
    val mediaUrl = detail.fullImageUrl.ifBlank { detail.previewImageUrl }.ifBlank { thumbnailUrl }
    val zoomImageUrl = mediaUrl.trim()
    val browseFilter = remember(detail.category, detail.type, detail.species) {
        SubmissionBrowseFilter(
            category = FaBrowseTaxonomyOptions.findCategoryIdByLabel(detail.category),
            type = FaBrowseTaxonomyOptions.findTypeIdByLabel(detail.type),
            species = FaBrowseTaxonomyOptions.findSpeciesIdByLabel(detail.species),
        )
    }
    val keywordChips = remember(detail) {
        detail.keywords
            .filter { chip -> chip.isNotBlank() }
            .distinct()
            .take(12)
    }
    val shouldBlurBlockedMedia = isBlockedByTag &&
            blockedSubmissionMode == BlockedSubmissionPagerMode.BLUR_THEN_OPEN &&
            !isBlockedMediaRevealed
    val detailMediaInteractionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                    },
                ),
        ) {
            NetworkImage(
                url = mediaUrl,
                modifier = Modifier
                    .fillMaxSize()
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
                metrics.forEach { metric ->
                    SubmissionInfoMetricChip(metric = metric)
                }
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
            category = detail.category,
            type = detail.type,
            species = detail.species,
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
