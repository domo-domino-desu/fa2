package me.domino.fa2.ui.pages.search.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.components.ExpressiveFilledTonalButton
import me.domino.fa2.ui.components.ExpressiveTextButton
import me.domino.fa2.ui.components.accessibleReadOnlyFieldTrigger
import me.domino.fa2.ui.components.presentationOnlySemantics
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.pages.search.util.SearchDateFields
import me.domino.fa2.ui.pages.search.util.SearchDateRangeShiftAction
import me.domino.fa2.ui.pages.search.util.currentSearchDateBounds
import me.domino.fa2.ui.pages.search.util.epochMillisToIsoDate
import me.domino.fa2.ui.pages.search.util.isoDateToEpochMillisOrNull
import org.jetbrains.compose.resources.stringResource

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
    val bounds = currentSearchDateBounds()
    val datePickerState =
        androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis =
                isoDateToEpochMillisOrNull(initialValue)
                    ?: isoDateToEpochMillisOrNull(bounds.maxIsoDate)
        )
    DatePickerDialog(
        onDismissRequest = { pickerTarget = null },
        confirmButton = {
          ExpressiveTextButton(
              onClick = {
                val selected =
                    datePickerState.selectedDateMillis?.let { millis ->
                      epochMillisToIsoDate(millis)
                    }
                if (!selected.isNullOrBlank()) {
                  when (activeTarget) {
                    ManualDateFieldTarget.From -> onUpdateRangeFrom(selected)
                    ManualDateFieldTarget.To -> onUpdateRangeTo(selected)
                  }
                }
                pickerTarget = null
              }
          ) {
            Text(stringResource(Res.string.confirm))
          }
        },
        dismissButton = {
          ExpressiveTextButton(onClick = { pickerTarget = null }) {
            Text(stringResource(Res.string.cancel))
          }
        },
    ) {
      DatePicker(state = datePickerState, showModeToggle = false)
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val singleColumn = maxWidth < 560.dp
    if (singleColumn) {
      Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
      ) {
        ManualDateField(
            value = rangeFrom,
            label = stringResource(Res.string.date_from_label),
            onClick = { pickerTarget = ManualDateFieldTarget.From },
            modifier = Modifier.fillMaxWidth(),
        )
        ManualDateField(
            value = rangeTo,
            label = stringResource(Res.string.date_to_label),
            onClick = { pickerTarget = ManualDateFieldTarget.To },
            modifier = Modifier.fillMaxWidth(),
        )
      }
    } else {
      Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
      ) {
        ManualDateField(
            value = rangeFrom,
            label = stringResource(Res.string.date_from_label),
            onClick = { pickerTarget = ManualDateFieldTarget.From },
            modifier = Modifier.weight(1f),
        )
        ManualDateField(
            value = rangeTo,
            label = stringResource(Res.string.date_to_label),
            onClick = { pickerTarget = ManualDateFieldTarget.To },
            modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
internal fun SearchDateShiftButtons(
    fields: SearchDateFields?,
    canShiftPreviousYear: Boolean,
    canShiftPreviousMonth: Boolean,
    canShiftPreviousDay: Boolean,
    canShiftNextDay: Boolean,
    canShiftNextMonth: Boolean,
    canShiftNextYear: Boolean,
    onShift: (SearchDateRangeShiftAction) -> Unit,
) {
  val buttons =
      listOf(
          SearchDateShiftButtonModel(
              label = stringResource(Res.string.search_date_previous_year),
              action = SearchDateRangeShiftAction.PreviousYear,
              enabled = canShiftPreviousYear,
          ),
          SearchDateShiftButtonModel(
              label = stringResource(Res.string.search_date_previous_month),
              action = SearchDateRangeShiftAction.PreviousMonth,
              enabled = canShiftPreviousMonth,
          ),
          SearchDateShiftButtonModel(
              label = stringResource(Res.string.search_date_previous_day),
              action = SearchDateRangeShiftAction.PreviousDay,
              enabled = canShiftPreviousDay,
          ),
          SearchDateShiftButtonModel(
              label = stringResource(Res.string.search_date_next_year),
              action = SearchDateRangeShiftAction.NextYear,
              enabled = canShiftNextYear,
          ),
          SearchDateShiftButtonModel(
              label = stringResource(Res.string.search_date_next_month),
              action = SearchDateRangeShiftAction.NextMonth,
              enabled = canShiftNextMonth,
          ),
          SearchDateShiftButtonModel(
              label = stringResource(Res.string.search_date_next_day),
              action = SearchDateRangeShiftAction.NextDay,
              enabled = canShiftNextDay,
          ),
      )
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val singleColumn = maxWidth < 560.dp
    val rows = buttons.chunked(if (singleColumn) 2 else 3)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      rows.forEach { rowButtons ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          rowButtons.forEach { button ->
            ExpressiveFilledTonalButton(
                onClick = { onShift(button.action) },
                enabled = fields != null && button.enabled,
                modifier = Modifier.weight(1f),
            ) {
              Text(button.label)
            }
          }
        }
      }
    }
  }
}

private data class SearchDateShiftButtonModel(
    val label: String,
    val action: SearchDateRangeShiftAction,
    val enabled: Boolean,
)

@Composable
private fun ManualDateField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val emptyValueText = stringResource(Res.string.accessibility_not_set)
  val actionLabel = stringResource(Res.string.accessibility_open_field, label)
  Box(modifier = modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(stringResource(Res.string.date_placeholder)) },
        trailingIcon = {
          Icon(
              imageVector = FaMaterialSymbols.Outlined.DateRange,
              contentDescription = stringResource(Res.string.select_date),
          )
        },
        modifier = Modifier.fillMaxWidth().presentationOnlySemantics(),
    )
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(56.dp)
                .accessibleReadOnlyFieldTrigger(
                    label = label,
                    value = value,
                    emptyValue = emptyValueText,
                    actionLabel = actionLabel,
                    onClick = onClick,
                )
    )
  }
}
