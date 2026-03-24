package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubmissionKeywordsSection(
    keywordChips: List<String>,
    blockedKeywords: Set<String>,
    onSearchKeyword: (String) -> Unit,
    onKeywordLongPress: (String) -> Unit,
) {
  val visibleKeywordChips = keywordChips.filter { chip -> chip.isNotBlank() }.distinct()
  if (visibleKeywordChips.isEmpty()) return

  Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    SubmissionTagFlowRow(
        chips = visibleKeywordChips,
        blockedKeywords = blockedKeywords,
        onChipClick = { keyword -> onSearchKeyword("@keywords $keyword") },
        onChipLongClick = onKeywordLongPress,
        singleLineChipText = false,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubmissionTagFlowRow(
    chips: List<String>,
    blockedKeywords: Set<String>,
    onChipClick: ((String) -> Unit)?,
    onChipLongClick: ((String) -> Unit)?,
    singleLineChipText: Boolean,
) {
  if (chips.isEmpty()) return
  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    chips.forEach { chip ->
      val normalizedChip = chip.trim().lowercase()
      val isBlocked = normalizedChip in blockedKeywords
      val chipModifier =
          if (onChipClick != null || onChipLongClick != null) {
            Modifier.combinedClickable(
                onClick = { onChipClick?.invoke(chip) },
                onLongClick = { onChipLongClick?.invoke(chip) },
            )
          } else {
            Modifier
          }
      Surface(
          color =
              if (isBlocked) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
              } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
              },
          shape = RoundedCornerShape(999.dp),
          modifier = chipModifier,
      ) {
        Text(
            text = chip,
            style = MaterialTheme.typography.labelMedium,
            maxLines = if (singleLineChipText) 1 else Int.MAX_VALUE,
            overflow = if (singleLineChipText) TextOverflow.Ellipsis else TextOverflow.Clip,
            color =
                if (isBlocked) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.onSecondaryContainer
                },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }
  }
}
