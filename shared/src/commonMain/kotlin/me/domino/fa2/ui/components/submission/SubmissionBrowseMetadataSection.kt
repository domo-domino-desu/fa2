package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.host.LocalAppI18n
import me.domino.fa2.ui.host.LocalSearchUiLabelsRepository
import me.domino.fa2.ui.search.SearchUiMetadataKey

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubmissionBrowseMetadataSection(
    rating: String,
    category: String,
    type: String,
    species: String,
    browseFilter: SubmissionBrowseFilter,
    onOpenBrowseFilter: (category: Int, type: Int, species: Int) -> Unit,
) {
  val appI18n = LocalAppI18n.current
  val searchUiLabelsRepository = LocalSearchUiLabelsRepository.current
  val normalizedRating = rating.trim()
  val normalizedCategory = category.trim()
  val normalizedType = type.trim()
  val normalizedSpecies = species.trim()
  if (
      normalizedRating.isBlank() &&
          normalizedCategory.isBlank() &&
          normalizedType.isBlank() &&
          normalizedSpecies.isBlank()
  ) {
    return
  }

  FlowRow(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    SubmissionBrowseMetadataField(
        label =
            searchUiLabelsRepository.metadataLabel(SearchUiMetadataKey.RATING, appI18n.metadata),
        value = normalizedRating,
        onClick = null,
    )
    SubmissionBrowseMetadataField(
        label =
            searchUiLabelsRepository.metadataLabel(
                SearchUiMetadataKey.CATEGORY,
                appI18n.metadata,
            ),
        value = normalizedCategory,
        onClick =
            if (browseFilter.category != null) {
              { onOpenBrowseFilter(browseFilter.category, 1, 1) }
            } else {
              null
            },
    )
    SubmissionBrowseMetadataField(
        label = searchUiLabelsRepository.metadataLabel(SearchUiMetadataKey.TYPE, appI18n.metadata),
        value = normalizedType,
        onClick =
            if (browseFilter.type != null) {
              { onOpenBrowseFilter(1, browseFilter.type, 1) }
            } else {
              null
            },
    )
    SubmissionBrowseMetadataField(
        label =
            searchUiLabelsRepository.metadataLabel(
                SearchUiMetadataKey.SPECIES,
                appI18n.metadata,
            ),
        value = normalizedSpecies,
        onClick =
            if (browseFilter.species != null) {
              { onOpenBrowseFilter(1, 1, browseFilter.species) }
            } else {
              null
            },
    )
  }
}

@Composable
private fun SubmissionBrowseMetadataField(label: String, value: String, onClick: (() -> Unit)?) {
  val appI18n = LocalAppI18n.current
  val searchUiLabelsRepository = LocalSearchUiLabelsRepository.current
  if (value.isBlank()) return
  Surface(
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
      shape = RoundedCornerShape(999.dp),
      modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
  ) {
    Text(
        text = searchUiLabelsRepository.formatLabelValue(label, value, appI18n.uiLanguage),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
  }
}
