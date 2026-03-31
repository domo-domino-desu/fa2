package me.domino.fa2.di

import eu.anifantakis.lib.ksafe.KSafe
import me.domino.fa2.application.attachmenttext.AttachmentTextService
import me.domino.fa2.application.ocr.KtorSubmissionImageOcrService
import me.domino.fa2.application.ocr.SubmissionImageOcrService
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolver
import me.domino.fa2.application.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.application.translation.SubmissionImageOcrTranslationService
import me.domino.fa2.application.watchrecommendation.WatchRecommendationService
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.data.repository.AuthRepository
import me.domino.fa2.data.repository.BrowseRepository
import me.domino.fa2.data.repository.FavoritesRepository
import me.domino.fa2.data.repository.FeedRepository
import me.domino.fa2.data.repository.GalleryRepository
import me.domino.fa2.data.repository.JournalDetailRepository
import me.domino.fa2.data.repository.JournalRepository
import me.domino.fa2.data.repository.JournalsRepository
import me.domino.fa2.data.repository.PersistedWatchRecommendationCooldownRepository
import me.domino.fa2.data.repository.SearchRepository
import me.domino.fa2.data.repository.SubmissionDetailRepository
import me.domino.fa2.data.repository.SubmissionRepository
import me.domino.fa2.data.repository.UserRepository
import me.domino.fa2.data.repository.WatchRecommendationCooldownRepository
import me.domino.fa2.data.repository.WatchlistRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.AppSettingsStorage
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.ui.search.SearchUiLabelsRepository
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Repository 依赖模块。 */
fun repositoryModule(): Module = module {
  single {
    AppSettingsStorage(
        kv = get(),
        secretVault = get<KSafe>(qualifier = named(KOIN_QUALIFIER_SETTINGS_SECRET_VAULT)),
    )
  }
  single { AppSettingsService(get()) }
  single { FaTaxonomyRepository() }
  single { SearchUiLabelsRepository() }
  single { AttachmentTextService(get()) }
  single<SubmissionImageOcrService> { KtorSubmissionImageOcrService(get(), get()) }
  single { SubmissionImageOcrTranslationService(get(), get()) }
  single { ActivityHistoryRepository(get()) }
  single { SubmissionDescriptionTranslationService(get(), get()) }
  single { AuthRepository(get()) }
  single { FeedRepository(get()) }
  single { BrowseRepository(get()) }
  single { SearchRepository(get()) }
  single { SubmissionRepository(get(), get(), get(), get()) } bind SubmissionDetailRepository::class
  single { SubmissionSeriesResolver(get()) }
  single { PersistedWatchRecommendationCooldownRepository(get()) } bind
      WatchRecommendationCooldownRepository::class
  single { WatchRecommendationService(get<WatchlistRepository>(), get()) }
  single { UserRepository(get(), get()) }
  single { GalleryRepository(get()) }
  single { FavoritesRepository(get()) }
  single { JournalsRepository(get()) }
  single { JournalRepository(get()) } bind JournalDetailRepository::class
  single { WatchlistRepository(get()) }
}
