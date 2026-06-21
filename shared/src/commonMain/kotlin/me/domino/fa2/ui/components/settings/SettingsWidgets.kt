package me.domino.fa2.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
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
      color = MaterialTheme.colorScheme.surfaceContainer,
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
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
      )
    }
  }
}

/** 设置项统一左右结构：左侧标题/说明，右侧控件。 */
@Composable
fun SettingsControlRow(
    title: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    BoxWithConstraints(
        modifier =
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(vertical = 6.dp)
    ) {
      val stacked = maxWidth < 320.dp
      if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SettingsControlLabel(title = title, supportingText = supportingText)
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            control()
          }
        }
      } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          SettingsControlLabel(
              title = title,
              supportingText = supportingText,
              modifier = Modifier.weight(1f),
          )
          Box(contentAlignment = Alignment.CenterEnd) { control() }
        }
      }
    }
  }
}

@Composable
private fun SettingsControlLabel(
    title: String,
    supportingText: String?,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
    )
    supportingText
        ?.takeIf { it.isNotBlank() }
        ?.let { text ->
          Text(
              text = text,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
  }
}

/** 设置页右侧输入框。 */
@Composable
fun SettingsInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
  SettingsControlRow(
      title = label,
      supportingText = supportingText,
      modifier = modifier,
  ) {
    CompactSettingsTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
    )
  }
}

/** 设置页纵向输入项：标题/说明在上，输入框在下占满整行。 */
@Composable
fun SettingsStackedInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    headerAction: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
  Column(
      modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
      SettingsControlLabel(
          title = label,
          supportingText = supportingText,
          modifier = Modifier.weight(1f),
      )
      headerAction?.invoke()
    }
    CompactSettingsTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(),
        constrainWidth = false,
    )
  }
}

@Composable
private fun CompactSettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    singleLine: Boolean,
    minLines: Int,
    visualTransformation: VisualTransformation,
    trailingIcon: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    constrainWidth: Boolean = true,
) {
  val shape = RoundedCornerShape(8.dp)
  val sizeModifier =
      if (constrainWidth) {
        modifier.widthIn(min = 88.dp, max = if (minLines > 1) 180.dp else 160.dp)
      } else {
        modifier.fillMaxWidth()
      }
  Surface(
      modifier =
          sizeModifier
              .heightIn(min = if (minLines > 1) 96.dp else 36.dp)
              .border(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                  shape = shape,
              ),
      shape = shape,
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
  ) {
    Row(
        modifier =
            Modifier.padding(horizontal = 10.dp, vertical = if (minLines > 1) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = if (minLines > 1) Alignment.Top else Alignment.CenterVertically,
    ) {
      BasicTextField(
          value = value,
          onValueChange = onValueChange,
          readOnly = readOnly,
          singleLine = singleLine,
          minLines = minLines,
          textStyle =
              MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          visualTransformation = visualTransformation,
          modifier = Modifier.weight(1f),
      )
      trailingIcon?.invoke()
    }
  }
}

/** 设置页进入子页面的列表项。 */
@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .accessibleClickableSummary(title = title, subtitle = subtitle)
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      SettingsControlLabel(
          title = title,
          supportingText = subtitle,
          modifier = Modifier.weight(1f),
      )
      Icon(
          imageVector = FaMaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SettingsRowDivider() {
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
}
