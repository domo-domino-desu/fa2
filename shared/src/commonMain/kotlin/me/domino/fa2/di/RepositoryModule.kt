package me.domino.fa2.di

import me.domino.fa2.data.repository.AuthRepository
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.data.repository.BrowseRepository
import me.domino.fa2.data.repository.FavoritesRepository
import me.domino.fa2.data.repository.FeedRepository
import me.domino.fa2.data.repository.GalleryRepository
import me.domino.fa2.data.repository.JournalRepository
import me.domino.fa2.data.repository.JournalsRepository
import me.domino.fa2.data.repository.SearchRepository
import me.domino.fa2.data.repository.SubmissionRepository
import me.domino.fa2.data.repository.UserRepository
import me.domino.fa2.data.repository.WatchlistRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.AppSettingsStorage
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Repository 依赖模块。
 */
fun repositoryModule(): Module = module {
    single { AppSettingsStorage(get()) }
    single { AppSettingsService(get()) }
    single { ActivityHistoryRepository(get()) }
    single { SubmissionDescriptionTranslationService(get(), get()) }
    single { AuthRepository(get()) }
    single { FeedRepository(get()) }
    single { BrowseRepository(get()) }
    single { SearchRepository(get()) }
    single { SubmissionRepository(get(), get(), get()) }
    single { UserRepository(get(), get()) }
    single { GalleryRepository(get()) }
    single { FavoritesRepository(get()) }
    single { JournalsRepository(get()) }
    single { JournalRepository(get()) }
    single { WatchlistRepository(get()) }
}
