package me.domino.fa2.ui.screen.search.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.screen.search.util.epochMillisToIsoDate
import me.domino.fa2.ui.screen.search.util.isoDateToEpochMillisOrNull

private enum class ManualDateFieldTarget {
  From,
  To,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualDateRangeSection(
  rangeFrom: String,
  rangeTo: String,
  onUpdateRangeFrom: (String) -> Unit,
  onUpdateRangeTo: (String) -> Unit,
) {
  var pickerTarget by remember { mutableStateOf<ManualDateFieldTarget?>(null) }
  val activeTarget = pickerTarget
  if (activeTarget != null) {
    val initialValue =
      when (activeTarget) {
        ManualDateFieldTarget.From -> rangeFrom
        ManualDateFieldTarget.To -> rangeTo
      }
    val datePickerState =
      androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = isoDateToEpochMillisOrNull(initialValue)
      )
    DatePickerDialog(
      onDismissRequest = { pickerTarget = null },
      confirmButton = {
        TextButton(
          onClick = {
            val selected =
              datePickerState.selectedDateMillis?.let { millis -> epochMillisToIsoDate(millis) }
            if (!selected.isNullOrBlank()) {
              when (activeTarget) {
                ManualDateFieldTarget.From -> onUpdateRangeFrom(selected)
                ManualDateFieldTarget.To -> onUpdateRangeTo(selected)
              }
            }
            pickerTarget = null
          }
        ) {
          Text("确定")
        }
      },
      dismissButton = { TextButton(onClick = { pickerTarget = null }) { Text("取消") } },
    ) {
      DatePicker(state = datePickerState, showModeToggle = false)
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val singleColumn = maxWidth < 560.dp
    if (singleColumn) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ManualDateField(
          value = rangeFrom,
          label = "From (YYYY-MM-DD)",
          onClick = { pickerTarget = ManualDateFieldTarget.From },
          modifier = Modifier.fillMaxWidth(),
        )
        ManualDateField(
          value = rangeTo,
          label = "To (YYYY-MM-DD)",
          onClick = { pickerTarget = ManualDateFieldTarget.To },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    } else {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ManualDateField(
          value = rangeFrom,
          label = "From (YYYY-MM-DD)",
          onClick = { pickerTarget = ManualDateFieldTarget.From },
          modifier = Modifier.weight(1f),
        )
        ManualDateField(
          value = rangeTo,
          label = "To (YYYY-MM-DD)",
          onClick = { pickerTarget = ManualDateFieldTarget.To },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun ManualDateField(
  value: String,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    OutlinedTextField(
      value = value,
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      placeholder = { Text("YYYY-MM-DD") },
      trailingIcon = { Icon(imageVector = Icons.Outlined.DateRange, contentDescription = "选择日期") },
      modifier = Modifier.fillMaxWidth(),
    )
    Box(modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick))
  }
}
