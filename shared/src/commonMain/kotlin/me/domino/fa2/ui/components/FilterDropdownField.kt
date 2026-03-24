package me.domino.fa2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** 通用筛选下拉项。 */
data class FilterOption<T>(val value: T, val label: String)

/** 筛选分组。 */
data class FilterOptionGroup<T>(val label: String, val options: List<FilterOption<T>>)

/** 简单下拉筛选框。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FilterDropdownField(
    label: String,
    options: List<FilterOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel = options.firstOrNull { option -> option.value == selected }?.label.orEmpty()

  ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = !expanded },
      modifier = modifier,
  ) {
    OutlinedTextField(
        value = selectedLabel,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        modifier =
            Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .onFocusChanged { if (it.isFocused) expanded = true },
    )

    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { option ->
        DropdownMenuItem(
            text = { Text(option.label) },
            onClick = {
              onSelected(option.value)
              expanded = false
            },
        )
      }
    }
  }
}

/** 带分组标题的下拉筛选框。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> GroupedFilterDropdownField(
    label: String,
    groups: List<FilterOptionGroup<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val options = groups.flatMap { group -> group.options }
  val selectedLabel = options.firstOrNull { option -> option.value == selected }?.label.orEmpty()

  ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = !expanded },
      modifier = modifier,
  ) {
    OutlinedTextField(
        value = selectedLabel,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        modifier =
            Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .onFocusChanged { if (it.isFocused) expanded = true },
    )

    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      groups.forEach { group ->
        if (group.label.isNotBlank()) {
          DropdownMenuItem(
              text = {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              },
              onClick = {},
              enabled = false,
          )
        }
        group.options.forEach { option ->
          DropdownMenuItem(
              text = { Text(option.label) },
              onClick = {
                onSelected(option.value)
                expanded = false
              },
          )
        }
      }
    }
  }
}

/** 点击后打开弹窗选择器的只读字段。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialogTriggerField(
    label: String,
    valueLabel: String,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    OutlinedTextField(
        value = valueLabel,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Box(modifier = Modifier.matchParentSize().clickable(onClick = onOpenPicker))
  }
}

/** 纯文本可滚动分组选择弹窗。 */
@Composable
fun <T> GroupedTextPickerDialog(
    title: String,
    groups: List<FilterOptionGroup<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
  val listState = rememberLazyListState()
  val selectedGroupIndex =
      remember(groups, selected) {
        groups.indexOfFirst { group -> group.options.any { option -> option.value == selected } }
      }
  LaunchedEffect(selectedGroupIndex) {
    if (selectedGroupIndex >= 0) {
      // 第 0 项是标题，分组从第 1 项开始。
      listState.scrollToItem(selectedGroupIndex + 1)
    }
  }

  Dialog(onDismissRequest = onDismissRequest) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
    ) {
      LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        item(key = "picker-title") {
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onDismissRequest) {
              Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "关闭",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        groups.forEach { group ->
          item(key = "picker-group-${group.label}") {
            GroupedTextPickerSection(
                group = group,
                selected = selected,
                onSelected = onSelected,
                onDismissRequest = onDismissRequest,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> GroupedTextPickerSection(
    group: FilterOptionGroup<T>,
    selected: T,
    onSelected: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
  Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (group.label.isNotBlank()) {
      Text(
          text = group.label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
      group.options.forEach { option ->
        val isSelected = option.value == selected
        val bringIntoViewRequester = remember(option.value) { BringIntoViewRequester() }
        LaunchedEffect(isSelected) {
          if (isSelected) {
            bringIntoViewRequester.bringIntoView()
          }
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color =
                if (isSelected) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
            modifier =
                Modifier.bringIntoViewRequester(bringIntoViewRequester).clickable {
                  onSelected(option.value)
                  onDismissRequest()
                },
        )
      }
    }
  }
}
