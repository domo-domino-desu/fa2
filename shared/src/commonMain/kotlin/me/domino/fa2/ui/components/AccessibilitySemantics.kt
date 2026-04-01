package me.domino.fa2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal fun accessibilitySummary(primary: String, secondary: String? = null): String =
    listOfNotNull(primary.trim().ifBlank { null }, secondary?.trim()?.ifBlank { null })
        .joinToString(separator = ", ")

internal fun Modifier.presentationOnlySemantics(): Modifier = clearAndSetSemantics {}

internal fun Modifier.accessibilityHeading(): Modifier = semantics { heading() }

internal fun Modifier.accessibleReadOnlyFieldTrigger(
    label: String,
    value: String,
    emptyValue: String,
    actionLabel: String,
    onClick: () -> Unit,
): Modifier =
    clickable(onClick = onClick).clearAndSetSemantics {
      role = Role.Button
      contentDescription = label
      stateDescription = value.trim().ifBlank { emptyValue }
      onClick(label = actionLabel) {
        onClick()
        true
      }
    }

internal fun Modifier.accessibleToggleRow(
    label: String,
    checked: Boolean,
    role: Role,
    onCheckedChange: (Boolean) -> Unit,
): Modifier =
    defaultMinSize(minHeight = 48.dp)
        .toggleable(value = checked, role = role, onValueChange = onCheckedChange)
        .semantics(mergeDescendants = true) { contentDescription = label }

internal fun Modifier.accessibleCombinedAction(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier =
    defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .semantics { contentDescription = label }

internal fun Modifier.accessibleClickableSummary(
    title: String,
    subtitle: String? = null,
    mergeDescendants: Boolean = true,
): Modifier =
    semantics(mergeDescendants = mergeDescendants) {
      role = Role.Button
      contentDescription = accessibilitySummary(title, subtitle)
    }

internal fun accessibilitySnackbarModifier(): Modifier =
    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
