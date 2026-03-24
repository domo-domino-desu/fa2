package me.domino.fa2.di

import me.domino.fa2.data.store.BrowseStore
import me.domino.fa2.data.store.FeedStore
import me.domino.fa2.data.store.GalleryStore
import me.domino.fa2.data.store.JournalStore
import me.domino.fa2.data.store.JournalsStore
import me.domino.fa2.data.store.SearchStore
import me.domino.fa2.data.store.SubmissionStore
import me.domino.fa2.data.store.UserStore
import me.domino.fa2.data.store.WatchlistStore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Store 依赖模块。
 */
fun storeModule(): Module = module {
    single { FeedStore(dataSource = get(), pageCacheDao = get()) }
    single { BrowseStore(dataSource = get(), pageCacheDao = get()) }
    single { SearchStore(dataSource = get(), pageCacheDao = get()) }
    single { SubmissionStore(dataSource = get(), pageCacheDao = get()) }
    single { UserStore(dataSource = get(), pageCacheDao = get()) }
    single {
        GalleryStore(
            galleryDataSource = get(),
            favoritesDataSource = get(),
            pageCacheDao = get(),
        )
    }
    single { JournalsStore(dataSource = get(), pageCacheDao = get()) }
    single { JournalStore(dataSource = get(), pageCacheDao = get()) }
    single { WatchlistStore(dataSource = get(), pageCacheDao = get()) }
}
