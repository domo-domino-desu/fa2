package me.domino.fa2.ui.pages.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.ui.layouts.SearchHistoryRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.pages.search.SearchRouteScreen
import org.koin.compose.koinInject

/** 搜索历史页面（纯文字列表）。 */
class SearchHistoryRouteScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val historyRepository = koinInject<ActivityHistoryRepository>()
    var loading by remember { mutableStateOf(true) }
    var histories by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
      histories = historyRepository.loadSearchHistory()
      loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
      SearchHistoryRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
      )

      when {
        loading -> {
          Text(
              text = "正在加载搜索记录...",
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          )
        }

        histories.isEmpty() -> {
          Text(
              text = "暂无搜索记录。",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          )
        }

        else -> {
          LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(items = histories, key = { query -> query }) { query ->
              Surface(
                  color = MaterialTheme.colorScheme.surface,
                  shape = RoundedCornerShape(14.dp),
                  border =
                      BorderStroke(
                          width = 1.dp,
                          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f),
                      ),
                  modifier =
                      Modifier.fillMaxWidth().clickable {
                        navigator.push(SearchRouteScreen(initialQuery = query))
                      },
              ) {
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}
