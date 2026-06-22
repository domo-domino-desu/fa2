package me.domino.fa2.ui.pages.settings.components

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import me.domino.fa2.ui.components.accessibleToggleRow
import me.domino.fa2.ui.components.presentationOnlySemantics

@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
) {
  SettingsControlRow(
      title = label,
      supportingText = supportingText,
      modifier =
          Modifier.accessibleToggleRow(
              label = label,
              checked = checked,
              role = Role.Switch,
              onCheckedChange = onCheckedChange,
          ),
  ) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        modifier = Modifier.presentationOnlySemantics(),
    )
  }
}
