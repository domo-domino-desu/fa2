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
  selectedGenders: Set<SearchGender>,
  onToggleGender: (SearchGender, Boolean) -> Unit,
) {
  val options =
    SearchGender.entries.map { gender ->
      GenderOption(gender = gender, label = gender.token, checked = gender in selectedGenders)
    }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(text = "Gender Keywords", style = MaterialTheme.typography.titleSmall)
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
  general: Boolean,
  mature: Boolean,
  adult: Boolean,
  onSetGeneral: (Boolean) -> Unit,
  onSetMature: (Boolean) -> Unit,
  onSetAdult: (Boolean) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("Ratings", style = MaterialTheme.typography.titleSmall)
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      RatingItem("General", general, onSetGeneral)
      RatingItem("Mature", mature, onSetMature)
      RatingItem("Adult", adult, onSetAdult)
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SubmissionTypeSection(
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
    Text("Submission Types", style = MaterialTheme.typography.titleSmall)
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      RatingItem("Art", typeArt, onSetTypeArt)
      RatingItem("Music", typeMusic, onSetTypeMusic)
      RatingItem("Flash", typeFlash, onSetTypeFlash)
      RatingItem("Story", typeStory, onSetTypeStory)
      RatingItem("Photo", typePhoto, onSetTypePhoto)
      RatingItem("Poetry", typePoetry, onSetTypePoetry)
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
