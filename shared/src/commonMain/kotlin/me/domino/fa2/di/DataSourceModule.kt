package me.domino.fa2.di

import me.domino.fa2.data.datasource.AuthDataSource
import me.domino.fa2.data.datasource.BrowseDataSource
import me.domino.fa2.data.datasource.FavoritesDataSource
import me.domino.fa2.data.datasource.FeedDataSource
import me.domino.fa2.data.datasource.GalleryDataSource
import me.domino.fa2.data.datasource.JournalDataSource
import me.domino.fa2.data.datasource.JournalsDataSource
import me.domino.fa2.data.datasource.SearchDataSource
import me.domino.fa2.data.datasource.SubmissionDataSource
import me.domino.fa2.data.datasource.UserDataSource
import me.domino.fa2.data.datasource.WatchlistDataSource
import me.domino.fa2.data.parser.FeedParser
import me.domino.fa2.data.parser.GalleryParser
import me.domino.fa2.data.parser.JournalParser
import me.domino.fa2.data.parser.JournalsParser
import me.domino.fa2.data.parser.SubmissionParser
import me.domino.fa2.data.parser.UserParser
import me.domino.fa2.data.parser.WatchlistParser
import org.koin.core.module.Module
import org.koin.dsl.module

/** DataSource 依赖模块。 */
fun dataSourceModule(): Module = module {
  single { FeedParser() }
  single { SubmissionParser() }
  single { UserParser() }
  single { GalleryParser() }
  single { JournalsParser() }
  single { JournalParser() }
  single { WatchlistParser() }

  single { AuthDataSource(homeEndpoint = get(), cookiesStorage = get(), userAgentStorage = get()) }

  single { FeedDataSource(get(), get()) }
  single { SubmissionDataSource(get(), get()) }
  single { UserDataSource(get(), get()) }
  single { GalleryDataSource(get(), get()) }
  single { FavoritesDataSource(get(), get()) }
  single { BrowseDataSource(get(), get()) }
  single { SearchDataSource(get(), get()) }
  single { JournalsDataSource(get(), get()) }
  single { JournalDataSource(get(), get()) }
  single { WatchlistDataSource(get(), get()) }
}
