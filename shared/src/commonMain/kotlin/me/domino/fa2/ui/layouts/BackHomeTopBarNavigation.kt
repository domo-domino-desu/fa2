package me.domino.fa2.ui.layouts

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.icons.FaMaterialSymbols
import org.jetbrains.compose.resources.stringResource

/** TopBar 左侧 Back + Home 导航区。 */
@Composable
fun BackHomeTopBarNavigation(onBack: () -> Unit, onGoHome: () -> Unit) {
  Row {
    IconButton(onClick = onBack) {
      Icon(
          imageVector = FaMaterialSymbols.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(Res.string.back),
      )
    }
    IconButton(onClick = onGoHome) {
      Icon(
          imageVector = FaMaterialSymbols.Outlined.Home,
          contentDescription = stringResource(Res.string.go_home),
      )
    }
  }
}
