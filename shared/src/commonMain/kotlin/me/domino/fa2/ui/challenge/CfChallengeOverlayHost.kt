package me.domino.fa2.ui.challenge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.domino.fa2.app.challenge.CfChallengeController
import me.domino.fa2.app.challenge.CfChallengeUiState
import org.koin.compose.koinInject

/**
 * 全局 challenge 覆盖层宿主。
 */
@Composable
fun CfChallengeOverlayHost(
    modifier: Modifier = Modifier,
) {
    val coordinator = koinInject<CfChallengeController>()
    val challengeState by coordinator.state.collectAsState()
    val activeState = challengeState as? CfChallengeUiState.Active ?: return
    CfChallengeOverlay(
        state = activeState,
        controller = coordinator,
        modifier = modifier,
    )
}
