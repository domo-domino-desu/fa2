package me.domino.fa2.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import me.domino.fa2.ui.components.accessibleToggleRow
import me.domino.fa2.ui.components.presentationOnlySemantics

@Composable
fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .accessibleToggleRow(
                  label = label,
                  checked = checked,
                  role = Role.Switch,
                  onCheckedChange = onCheckedChange,
              ),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
    )
    Switch(
        checked = checked,
        onCheckedChange = null,
        modifier = Modifier.presentationOnlySemantics(),
    )
  }
}
