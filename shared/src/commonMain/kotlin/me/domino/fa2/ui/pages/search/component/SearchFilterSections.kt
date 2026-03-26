package me.domino.fa2.ui.pages.search.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.pages.search.SearchGender

private data class GenderOption(val gender: SearchGender, val label: String, val checked: Boolean)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun GenderKeywordsSection(
    title: String,
    selectedGenders: Set<SearchGender>,
    labelForGender: (SearchGender) -> String,
    onToggleGender: (SearchGender, Boolean) -> Unit,
) {
  val options =
      SearchGender.entries.map { gender ->
        GenderOption(
            gender = gender,
            label = labelForGender(gender),
            checked = gender in selectedGenders,
        )
      }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(text = title, style = MaterialTheme.typography.titleSmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 3,
    ) {
      options.forEach { option ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
              checked = option.checked,
              onCheckedChange = { checked -> onToggleGender(option.gender, checked) },
          )
          Text(option.label)
        }
      }
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RatingsSection(
    title: String,
    generalLabel: String,
    matureLabel: String,
    adultLabel: String,
    general: Boolean,
    mature: Boolean,
    adult: Boolean,
    onSetGeneral: (Boolean) -> Unit,
    onSetMature: (Boolean) -> Unit,
    onSetAdult: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      RatingItem(generalLabel, general, onSetGeneral)
      RatingItem(matureLabel, mature, onSetMature)
      RatingItem(adultLabel, adult, onSetAdult)
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SubmissionTypeSection(
    title: String,
    artLabel: String,
    musicLabel: String,
    flashLabel: String,
    storyLabel: String,
    photoLabel: String,
    poetryLabel: String,
    typeArt: Boolean,
    typeMusic: Boolean,
    typeFlash: Boolean,
    typeStory: Boolean,
    typePhoto: Boolean,
    typePoetry: Boolean,
    onSetTypeArt: (Boolean) -> Unit,
    onSetTypeMusic: (Boolean) -> Unit,
    onSetTypeFlash: (Boolean) -> Unit,
    onSetTypeStory: (Boolean) -> Unit,
    onSetTypePhoto: (Boolean) -> Unit,
    onSetTypePoetry: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      RatingItem(artLabel, typeArt, onSetTypeArt)
      RatingItem(musicLabel, typeMusic, onSetTypeMusic)
      RatingItem(flashLabel, typeFlash, onSetTypeFlash)
      RatingItem(storyLabel, typeStory, onSetTypeStory)
      RatingItem(photoLabel, typePhoto, onSetTypePhoto)
      RatingItem(poetryLabel, typePoetry, onSetTypePoetry)
    }
  }
}

@Composable
private fun RatingItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Text(label)
  }
}
