package me.domino.fa2.ui.layouts

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/** TopBar 左侧 Back + Home 导航区。 */
@Composable
fun BackHomeTopBarNavigation(onBack: () -> Unit, onGoHome: () -> Unit) {
  Row {
    IconButton(onClick = onBack) {
      Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }
    IconButton(onClick = onGoHome) {
      Icon(imageVector = Icons.Filled.Home, contentDescription = "回到首页")
    }
  }
}
