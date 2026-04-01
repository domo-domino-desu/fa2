package me.domino.fa2.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.components.accessibilityHeading
import me.domino.fa2.ui.components.accessibleClickableSummary
import me.domino.fa2.ui.icons.FaMaterialSymbols

/** More 页顶部账号头。 */
@Composable
fun SettingsAccountHeader(
    /** 标题（通常为用户名）。 */
    title: String,
    /** 副标题。 */
    subtitle: String,
    /** 点击回调。 */
    onClick: () -> Unit,
    /** 是否可点击。 */
    enabled: Boolean = true,
    /** 修饰符。 */
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier =
          modifier
              .fillMaxWidth()
              .clickable(enabled = enabled, onClick = onClick)
              .accessibleClickableSummary(
                  title = title,
                  subtitle = subtitle,
                  mergeDescendants = false,
              ),
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surface,
      border =
          BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
          ),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
          modifier = Modifier.size(44.dp),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
              text = title.take(1).uppercase(),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** More 页分组容器。 */
@Composable
fun SettingsGroup(
    /** 分组标题。 */
    title: String? = null,
    /** 是否展示容器外框。 */
    framed: Boolean = true,
    /** 标题左右边距。 */
    titleHorizontalPadding: Dp = 18.dp,
    /** 外层容器左右边距。 */
    containerHorizontalPadding: Dp = 8.dp,
    /** 分组内容。 */
    content: @Composable ColumnScope.() -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (!title.isNullOrBlank()) {
      Text(
          text = title,
          modifier = Modifier.padding(horizontal = titleHorizontalPadding).accessibilityHeading(),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
      )
    }
    if (framed) {
      Card(
          modifier = Modifier.fillMaxWidth().padding(horizontal = containerHorizontalPadding),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border =
              BorderStroke(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
              ),
      ) {
        Column(content = content)
      }
    } else {
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = containerHorizontalPadding),
          content = content,
      )
    }
  }
}

/** More 页列表项。 */
@Composable
fun SettingsListItem(
    /** 左侧图标。 */
    icon: ImageVector,
    /** 主标题。 */
    title: String,
    /** 副标题。 */
    subtitle: String,
    /** 点击回调。 */
    onClick: () -> Unit,
    /** 是否可点击。 */
    enabled: Boolean = true,
    /** 是否展示底部分割线。 */
    showDivider: Boolean = true,
    /** 修饰符。 */
    modifier: Modifier = Modifier,
) {
  Column {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .accessibleClickableSummary(title = title, subtitle = subtitle)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
          modifier = Modifier.size(34.dp),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.secondaryContainer,
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Icon(
              imageVector = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
      Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color =
                if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
          imageVector = FaMaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (showDivider) {
      HorizontalDivider(
          modifier = Modifier.padding(start = 60.dp),
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      )
    }
  }
}
