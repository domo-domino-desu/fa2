package me.domino.fa2.ui.pages.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalUserHeaderContent = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }
internal val LocalUserHeaderRefreshAction = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalUserSubmissionFolderResolver =
    staticCompositionLocalOf<(UserChildRoute) -> String?> { { null } }
internal val LocalUserSubmissionFolderUpdater =
    staticCompositionLocalOf<(UserChildRoute, String) -> Unit> { { _, _ -> } }
internal val LocalUserSharedTopScrollStateResolver =
    staticCompositionLocalOf<() -> UserSharedTopScrollState> {
      { UserSharedTopScrollState.Inline() }
    }
internal val LocalUserSharedTopScrollStateUpdater =
    staticCompositionLocalOf<(UserSharedTopScrollState) -> Unit> { { _ -> } }
internal val LocalUserBodyScrollPositionResolver =
    staticCompositionLocalOf<(String) -> UserBodyScrollPosition?> { { null } }
internal val LocalUserBodyScrollPositionUpdater =
    staticCompositionLocalOf<(String, UserBodyScrollPosition) -> Unit> { { _, _ -> } }
internal val LocalUserSubmissionSnapshotResolver =
    staticCompositionLocalOf<(String) -> UserSubmissionSectionUiState?> { { null } }
internal val LocalUserSubmissionSnapshotUpdater =
    staticCompositionLocalOf<(String, UserSubmissionSectionUiState) -> Unit> { { _, _ -> } }
