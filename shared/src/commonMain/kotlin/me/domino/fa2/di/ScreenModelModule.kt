package me.domino.fa2.di

import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.ui.pages.auth.AuthScreenModel
import me.domino.fa2.ui.pages.browse.BrowseScreenModel
import me.domino.fa2.ui.pages.feed.FeedScreenModel
import me.domino.fa2.ui.pages.more.MoreScreenModel
import me.domino.fa2.ui.pages.search.SearchScreenModel
import me.domino.fa2.ui.pages.user.gallery.UserSubmissionSectionScreenModel
import me.domino.fa2.ui.pages.user.gallery.UserSubmissionSectionUiState
import me.domino.fa2.ui.pages.user.journal.JournalDetailScreenModel
import me.domino.fa2.ui.pages.user.journal.UserJournalsScreenModel
import me.domino.fa2.ui.pages.user.profile.UserScreenModel
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.shout.UserShoutsScreenModel
import me.domino.fa2.ui.pages.user.watchlist.UserWatchlistScreenModel
import me.domino.fa2.ui.pages.watchrecommendation.WatchRecommendationScreenModel
import org.koin.core.module.Module
import org.koin.dsl.module

/** ScreenModel 依赖模块。 */
fun screenModelModule(): Module = module {
  factory { AuthScreenModel(get(), get(), get()) }
  factory { FeedScreenModel(get(), get(), get()) }
  factory { BrowseScreenModel(get(), get(), get()) }
  factory { SearchScreenModel(get(), get(), get(), get(), get(), get()) }
  factory { (username: String) -> MoreScreenModel(username, get(), get(), get(), get()) }
  factory { (username: String, initialChildRoute: UserChildRoute, initialFolderUrl: String?) ->
    UserScreenModel(
        username = username,
        repository = get(),
        initialChildRoute = initialChildRoute,
        initialFolderUrl = initialFolderUrl,
        settingsService = get(),
        systemLanguageProvider = get(),
    )
  }
  factory {
      (
          username: String,
          route: UserChildRoute,
          initialFolderUrl: String?,
          initialSnapshot: UserSubmissionSectionUiState?,
      ) ->
    UserSubmissionSectionScreenModel(
        username = username,
        route = route,
        galleryRepository = get(),
        favoritesRepository = get(),
        initialFolderUrl = initialFolderUrl,
        initialSnapshot = initialSnapshot,
        settingsService = get(),
        systemLanguageProvider = get(),
    )
  }
  factory { (username: String) ->
    UserJournalsScreenModel(
        username = username,
        repository = get(),
        settingsService = get(),
        systemLanguageProvider = get(),
    )
  }
  factory { (username: String) ->
    UserShoutsScreenModel(
        username = username,
        repository = get(),
    )
  }
  factory { (journalId: Int, journalUrl: String?) ->
    JournalDetailScreenModel(
        journalId = journalId,
        journalUrl = journalUrl,
        repository = get(),
        translationService = get(),
        settingsService = get(),
        systemLanguageProvider = get(),
    )
  }
  factory { (username: String, category: WatchlistCategory, initialUrl: String?) ->
    UserWatchlistScreenModel(
        username = username,
        category = category,
        repository = get(),
        initialPageUrl = initialUrl,
        settingsService = get(),
        systemLanguageProvider = get(),
    )
  }
  factory { (username: String) ->
    WatchRecommendationScreenModel(
        username = username,
        recommendationService = get(),
        settingsService = get(),
    )
  }
}
