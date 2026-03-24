package me.domino.fa2.di

import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.ui.navigation.MoreScreenModel
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.screen.auth.AuthScreenModel
import me.domino.fa2.ui.screen.browse.BrowseScreenModel
import me.domino.fa2.ui.screen.feed.FeedScreenModel
import me.domino.fa2.ui.screen.search.SearchScreenModel
import me.domino.fa2.ui.screen.submission.SubmissionPagerDetailSourceImpl
import me.domino.fa2.ui.screen.submission.SubmissionPagerFeedSourceImpl
import me.domino.fa2.ui.screen.submission.SubmissionScreenModel
import me.domino.fa2.ui.screen.user.JournalDetailScreenModel
import me.domino.fa2.ui.screen.user.UserChildRoute
import me.domino.fa2.ui.screen.user.UserJournalsScreenModel
import me.domino.fa2.ui.screen.user.UserScreenModel
import me.domino.fa2.ui.screen.user.UserSubmissionSectionScreenModel
import me.domino.fa2.ui.screen.user.UserSubmissionSectionUiState
import me.domino.fa2.ui.screen.user.UserWatchlistScreenModel
import org.koin.core.module.Module
import org.koin.dsl.module

/** ScreenModel 依赖模块。 */
fun screenModelModule(): Module = module {
  factory { AuthScreenModel(get()) }
  factory { (holder: SubmissionListHolder) -> FeedScreenModel(get(), holder) }
  factory { (holder: SubmissionListHolder) -> BrowseScreenModel(get(), holder) }
  factory { (holder: SubmissionListHolder) -> SearchScreenModel(get(), holder) }
  factory { (username: String) -> MoreScreenModel(username, get(), get()) }
  factory { (initialSid: Int, holder: SubmissionListHolder) ->
    SubmissionScreenModel(
      initialSid = initialSid,
      holder = holder,
      feedSource = SubmissionPagerFeedSourceImpl(get()),
      submissionSource = SubmissionPagerDetailSourceImpl(get()),
    )
  }
  factory { (username: String, initialChildRoute: UserChildRoute, initialFolderUrl: String?) ->
    UserScreenModel(
      username = username,
      repository = get(),
      initialChildRoute = initialChildRoute,
      initialFolderUrl = initialFolderUrl,
    )
  }
  factory {
    (
      username: String,
      route: UserChildRoute,
      holder: SubmissionListHolder,
      initialFolderUrl: String?,
      initialSnapshot: UserSubmissionSectionUiState?,
    ) ->
    UserSubmissionSectionScreenModel(
      username = username,
      route = route,
      galleryRepository = get(),
      favoritesRepository = get(),
      submissionListHolder = holder,
      initialFolderUrl = initialFolderUrl,
      initialSnapshot = initialSnapshot,
    )
  }
  factory { (username: String) -> UserJournalsScreenModel(username = username, repository = get()) }
  factory { (journalId: Int, journalUrl: String?) ->
    JournalDetailScreenModel(journalId = journalId, journalUrl = journalUrl, repository = get())
  }
  factory { (username: String, category: WatchlistCategory, initialUrl: String?) ->
    UserWatchlistScreenModel(
      username = username,
      category = category,
      repository = get(),
      initialPageUrl = initialUrl,
    )
  }
}
