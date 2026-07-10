package me.domino.fa2.di

import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.data.local.AppDatabaseBuilderFactory
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.local.settings.AppSettingsStorage
import me.domino.fa2.data.local.taxonomy.FaTaxonomyRepository
import me.domino.fa2.data.local.watchrecommendation.PersistedWatchRecommendationBlocklistRepository
import me.domino.fa2.data.local.watchrecommendation.WatchRecommendationBlocklist
import me.domino.fa2.data.remote.fa.auth.AuthDataSource
import me.domino.fa2.data.remote.fa.auth.AuthRepository
import me.domino.fa2.data.remote.fa.auth.HomeEndpoint
import me.domino.fa2.data.remote.fa.browse.BrowseDataSource
import me.domino.fa2.data.remote.fa.browse.BrowseEndpoint
import me.domino.fa2.data.remote.fa.browse.BrowsePageCache
import me.domino.fa2.data.remote.fa.browse.BrowseRepository
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.favorites.FavoritesDataSource
import me.domino.fa2.data.remote.fa.favorites.FavoritesEndpoint
import me.domino.fa2.data.remote.fa.favorites.FavoritesRepository
import me.domino.fa2.data.remote.fa.feed.FeedDataSource
import me.domino.fa2.data.remote.fa.feed.FeedEndpoint
import me.domino.fa2.data.remote.fa.feed.FeedPageCache
import me.domino.fa2.data.remote.fa.feed.FeedParser
import me.domino.fa2.data.remote.fa.feed.FeedRepository
import me.domino.fa2.data.remote.fa.gallery.GalleryDataSource
import me.domino.fa2.data.remote.fa.gallery.GalleryEndpoint
import me.domino.fa2.data.remote.fa.gallery.GalleryRepository
import me.domino.fa2.data.remote.fa.journal.JournalDataSource
import me.domino.fa2.data.remote.fa.journal.JournalDetailRepository
import me.domino.fa2.data.remote.fa.journal.JournalEndpoint
import me.domino.fa2.data.remote.fa.journal.JournalPageCache
import me.domino.fa2.data.remote.fa.journal.JournalParser
import me.domino.fa2.data.remote.fa.journal.JournalRepository
import me.domino.fa2.data.remote.fa.journal.JournalsDataSource
import me.domino.fa2.data.remote.fa.journal.JournalsEndpoint
import me.domino.fa2.data.remote.fa.journal.JournalsPageCache
import me.domino.fa2.data.remote.fa.journal.JournalsParser
import me.domino.fa2.data.remote.fa.journal.JournalsRepository
import me.domino.fa2.data.remote.fa.listing.FavoritesCacheInvalidator
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser
import me.domino.fa2.data.remote.fa.listing.UserGalleryPageStore
import me.domino.fa2.data.remote.fa.media.AttachmentDownloadEndpoint
import me.domino.fa2.data.remote.fa.media.AttachmentDownloadSource
import me.domino.fa2.data.remote.fa.media.ImageBytesSource
import me.domino.fa2.data.remote.fa.media.ImageProgressTracker
import me.domino.fa2.data.remote.fa.media.KtorImageBytesSource
import me.domino.fa2.data.remote.fa.media.createCachedDownloadHttpClient
import me.domino.fa2.data.remote.fa.search.SearchDataSource
import me.domino.fa2.data.remote.fa.search.SearchEndpoint
import me.domino.fa2.data.remote.fa.search.SearchPageCache
import me.domino.fa2.data.remote.fa.search.SearchRepository
import me.domino.fa2.data.remote.fa.session.AuthAwareFaHtmlDataSource
import me.domino.fa2.data.remote.fa.session.AuthSessionController
import me.domino.fa2.data.remote.fa.session.AuthSessionProfileStore
import me.domino.fa2.data.remote.fa.session.CfChallengeController
import me.domino.fa2.data.remote.fa.session.CfChallengeCoordinator
import me.domino.fa2.data.remote.fa.session.ChallengeAwareFaHtmlDataSource
import me.domino.fa2.data.remote.fa.session.ChallengeCookiePolicy
import me.domino.fa2.data.remote.fa.session.ChallengeProbeVerifier
import me.domino.fa2.data.remote.fa.session.ChallengeResolver
import me.domino.fa2.data.remote.fa.session.ChallengeSessionStorage
import me.domino.fa2.data.remote.fa.session.CloudflareChallengeCookiePolicy
import me.domino.fa2.data.remote.fa.session.CookiePersistence
import me.domino.fa2.data.remote.fa.session.DefaultAuthSessionController
import me.domino.fa2.data.remote.fa.session.FaCookiesStorage
import me.domino.fa2.data.remote.fa.session.FaHttpClient
import me.domino.fa2.data.remote.fa.session.KSafeCookiePersistence
import me.domino.fa2.data.remote.fa.session.PendingFaRouteStore
import me.domino.fa2.data.remote.fa.session.UserAgentStorage
import me.domino.fa2.data.remote.fa.session.createFaPageHttpClient
import me.domino.fa2.data.remote.fa.social.SocialActionEndpoint
import me.domino.fa2.data.remote.fa.social.SocialActionSource
import me.domino.fa2.data.remote.fa.submission.SubmissionDataSource
import me.domino.fa2.data.remote.fa.submission.SubmissionEndpoint
import me.domino.fa2.data.remote.fa.submission.SubmissionPageCache
import me.domino.fa2.data.remote.fa.submission.SubmissionParser
import me.domino.fa2.data.remote.fa.submission.SubmissionRepository
import me.domino.fa2.data.remote.fa.submission.SubmissionSeriesSubmissionSource
import me.domino.fa2.data.remote.fa.user.ActivityHistoryRepository
import me.domino.fa2.data.remote.fa.user.UserDataSource
import me.domino.fa2.data.remote.fa.user.UserEndpoint
import me.domino.fa2.data.remote.fa.user.UserPageCache
import me.domino.fa2.data.remote.fa.user.UserParser
import me.domino.fa2.data.remote.fa.user.UserRepository
import me.domino.fa2.data.remote.fa.user.UserResourceRepository
import me.domino.fa2.data.remote.fa.user.WatchRecommendationUserSource
import me.domino.fa2.data.remote.fa.watchlist.WatchRecommendationWatchlistSource
import me.domino.fa2.data.remote.fa.watchlist.WatchlistDataSource
import me.domino.fa2.data.remote.fa.watchlist.WatchlistEndpoint
import me.domino.fa2.data.remote.fa.watchlist.WatchlistPageCache
import me.domino.fa2.data.remote.fa.watchlist.WatchlistParser
import me.domino.fa2.data.remote.fa.watchlist.WatchlistRepository
import me.domino.fa2.data.remote.translation.KtorTranslationPort
import me.domino.fa2.data.remote.translation.SubmissionTextTranslationEngine
import me.domino.fa2.data.remote.translation.TranslationPort
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.AppSettingsStore
import me.domino.fa2.domain.attachmenttext.AttachmentTextService
import me.domino.fa2.domain.ocr.DefaultSubmissionImageOcrService
import me.domino.fa2.domain.ocr.SubmissionImageOcrService
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Data layer and infrastructure dependencies. */
fun dataModule(): Module = module {
  single<AppDatabase> { get<AppDatabaseBuilderFactory>().create().build() }
  single { get<AppDatabase>().pageCacheDao() }
  single { get<AppDatabase>().historyDao() }
  single { KeyValueStorage(get()) }

  single<CookiePersistence> {
    KSafeCookiePersistence(cookieVault = get<KSafe>(qualifier = named(KOIN_QUALIFIER_COOKIE_VAULT)))
  }
  single { FaCookiesStorage(get()) }
  single { UserAgentStorage(get()) }
  single { ImageProgressTracker() }
  single { PendingFaRouteStore() }
  single<AuthSessionController> {
    DefaultAuthSessionController(profileStore = get(), pendingFaRouteStore = get())
  }
  single<HttpClient> { HttpClient { expectSuccess = false } }
  single<HttpClient>(qualifier = named(KOIN_QUALIFIER_FA_PAGE_CLIENT)) { createFaPageHttpClient() }
  single<HttpClient>(qualifier = named(KOIN_QUALIFIER_CACHED_DOWNLOAD_CLIENT)) {
    createCachedDownloadHttpClient(
        progressTracker = get(),
        cookiesStorage = get(),
        userAgentStorage = get(),
    )
  }
  single<TranslationPort> { KtorTranslationPort(get()) }
  single { SubmissionTextTranslationEngine(get()) }
  single<FaHtmlDataSource>(qualifier = named(KOIN_QUALIFIER_RAW_HTML_DATA_SOURCE)) {
    FaHttpClient(
        client = get(qualifier = named(KOIN_QUALIFIER_FA_PAGE_CLIENT)),
        cookiesStorage = get(),
        userAgentStorage = get(),
    )
  }
  single<ChallengeCookiePolicy> { CloudflareChallengeCookiePolicy() }
  single { ChallengeSessionStorage() }
  single {
    ChallengeProbeVerifier(
        rawHtmlDataSource = get(qualifier = named(KOIN_QUALIFIER_RAW_HTML_DATA_SOURCE))
    )
  }
  single {
    CfChallengeCoordinator(
        sessionStorage = get(),
        cookiesStorage = get(),
        userAgentStorage = get(),
        cookiePolicy = get(),
        probeVerifier = get(),
    )
  }
  single<CfChallengeController> { get<CfChallengeCoordinator>() }
  single<ChallengeResolver> { get<CfChallengeCoordinator>() }
  single<FaHtmlDataSource>(qualifier = named(KOIN_QUALIFIER_CHALLENGE_AWARE_HTML_DATA_SOURCE)) {
    ChallengeAwareFaHtmlDataSource(
        delegate = get(qualifier = named(KOIN_QUALIFIER_RAW_HTML_DATA_SOURCE)),
        challengeResolver = get<ChallengeResolver>(),
    )
  }
  single<FaHtmlDataSource> {
    AuthAwareFaHtmlDataSource(
        delegate = get(qualifier = named(KOIN_QUALIFIER_CHALLENGE_AWARE_HTML_DATA_SOURCE)),
        authSessionController = get(),
    )
  }
  single<HttpClient>(qualifier = named(KOIN_QUALIFIER_SOCIAL_ACTION_CLIENT)) {
    get<HttpClient>().config { followRedirects = false }
  }
  single<AttachmentDownloadSource> {
    AttachmentDownloadEndpoint(
        client = get(qualifier = named(KOIN_QUALIFIER_CACHED_DOWNLOAD_CLIENT)),
        challengeResolver = get<ChallengeResolver>(),
    )
  }
  single<ImageBytesSource> {
    KtorImageBytesSource(client = get(qualifier = named(KOIN_QUALIFIER_CACHED_DOWNLOAD_CLIENT)))
  }

  single { HomeEndpoint(get()) }
  single { FeedEndpoint(get()) }
  single { BrowseEndpoint(get()) }
  single { SearchEndpoint(get()) }
  single { SubmissionEndpoint(get()) }
  single { UserEndpoint(get()) }
  single { WatchlistEndpoint(get()) }
  single { GalleryEndpoint(get()) }
  single { FavoritesEndpoint(get()) }
  single { JournalsEndpoint(get()) }
  single { JournalEndpoint(get()) }
  single<SocialActionSource> {
    SocialActionEndpoint(
        client = get(qualifier = named(KOIN_QUALIFIER_SOCIAL_ACTION_CLIENT)),
        cookiesStorage = get(),
        userAgentStorage = get(),
        challengeResolver = get<ChallengeResolver>(),
    )
  }

  single { FeedParser() }
  single { SubmissionParser() }
  single { UserParser() }
  single { SubmissionListingParser() }
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

  single { FeedPageCache(dataSource = get(), pageCacheDao = get()) }
  single { BrowsePageCache(dataSource = get(), pageCacheDao = get()) }
  single { SearchPageCache(dataSource = get(), pageCacheDao = get()) }
  single { SubmissionPageCache(dataSource = get(), pageCacheDao = get()) }
  single { UserPageCache(dataSource = get(), pageCacheDao = get()) }
  single {
    UserGalleryPageStore(
        galleryDataSource = get<GalleryDataSource>(),
        favoritesDataSource = get<FavoritesDataSource>(),
        pageCacheDao = get(),
    )
  } bind FavoritesCacheInvalidator::class
  single { JournalsPageCache(dataSource = get(), pageCacheDao = get()) }
  single { JournalPageCache(dataSource = get(), pageCacheDao = get()) }
  single { WatchlistPageCache(dataSource = get(), pageCacheDao = get()) }

  single<AppSettingsStore> {
    AppSettingsStorage(
        kv = get(),
        secretVault = get<KSafe>(qualifier = named(KOIN_QUALIFIER_SETTINGS_SECRET_VAULT)),
    )
  }
  single { AppSettingsService(get()) }
  single { FaTaxonomyRepository() }
  single { AttachmentTextService(get()) }
  single<SubmissionImageOcrService> {
    DefaultSubmissionImageOcrService(imageBytesSource = get(), recognitionPort = get())
  }
  single { ActivityHistoryRepository(get()) }
  single { AuthRepository(get()) }
  single { AuthSessionProfileStore(get()) }
  single { FeedRepository(get()) }
  single { BrowseRepository(get()) }
  single { SearchRepository(get()) }
  single { SubmissionRepository(get(), get(), get()) } bind SubmissionSeriesSubmissionSource::class
  single { PersistedWatchRecommendationBlocklistRepository(get()) } bind
      WatchRecommendationBlocklist::class
  single { UserRepository(get(), get()) }
  single<UserResourceRepository> { get<UserRepository>() }
  single<WatchRecommendationUserSource> { get<UserRepository>() }
  single { GalleryRepository(get()) }
  single { FavoritesRepository(get()) }
  single { JournalsRepository(get()) }
  single { JournalRepository(get()) } bind JournalDetailRepository::class
  single { WatchlistRepository(get()) } bind WatchRecommendationWatchlistSource::class
}
