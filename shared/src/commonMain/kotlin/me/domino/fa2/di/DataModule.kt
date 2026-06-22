package me.domino.fa2.di

import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import me.domino.fa2.data.fa.auth.AuthDataSource
import me.domino.fa2.data.fa.auth.AuthRepository
import me.domino.fa2.data.fa.auth.AuthSessionProfileStore
import me.domino.fa2.data.fa.auth.HomeEndpoint
import me.domino.fa2.data.fa.browse.BrowseDataSource
import me.domino.fa2.data.fa.browse.BrowseEndpoint
import me.domino.fa2.data.fa.browse.BrowsePageCache
import me.domino.fa2.data.fa.browse.BrowseRepository
import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.FaHttpClient
import me.domino.fa2.data.fa.favorites.FavoritesDataSource
import me.domino.fa2.data.fa.favorites.FavoritesEndpoint
import me.domino.fa2.data.fa.favorites.FavoritesRepository
import me.domino.fa2.data.fa.feed.FeedDataSource
import me.domino.fa2.data.fa.feed.FeedEndpoint
import me.domino.fa2.data.fa.feed.FeedPageCache
import me.domino.fa2.data.fa.feed.FeedParser
import me.domino.fa2.data.fa.feed.FeedRepository
import me.domino.fa2.data.fa.gallery.GalleryDataSource
import me.domino.fa2.data.fa.gallery.GalleryEndpoint
import me.domino.fa2.data.fa.gallery.GalleryPageCache
import me.domino.fa2.data.fa.gallery.GalleryParser
import me.domino.fa2.data.fa.gallery.GalleryRepository
import me.domino.fa2.data.fa.journal.JournalDataSource
import me.domino.fa2.data.fa.journal.JournalEndpoint
import me.domino.fa2.data.fa.journal.JournalPageCache
import me.domino.fa2.data.fa.journal.JournalParser
import me.domino.fa2.data.fa.journal.JournalRepository
import me.domino.fa2.data.fa.journal.JournalsDataSource
import me.domino.fa2.data.fa.journal.JournalsEndpoint
import me.domino.fa2.data.fa.journal.JournalsPageCache
import me.domino.fa2.data.fa.journal.JournalsParser
import me.domino.fa2.data.fa.journal.JournalsRepository
import me.domino.fa2.data.fa.media.AttachmentDownloadEndpoint
import me.domino.fa2.data.fa.media.AttachmentDownloadSource
import me.domino.fa2.data.fa.media.ImageBytesSource
import me.domino.fa2.data.fa.media.ImageProgressTracker
import me.domino.fa2.data.fa.media.KtorImageBytesSource
import me.domino.fa2.data.fa.media.createCachedDownloadHttpClient
import me.domino.fa2.data.fa.search.SearchDataSource
import me.domino.fa2.data.fa.search.SearchEndpoint
import me.domino.fa2.data.fa.search.SearchPageCache
import me.domino.fa2.data.fa.search.SearchRepository
import me.domino.fa2.data.fa.session.AuthAwareFaHtmlDataSource
import me.domino.fa2.data.fa.session.AuthSessionController
import me.domino.fa2.data.fa.session.CfChallengeController
import me.domino.fa2.data.fa.session.CfChallengeCoordinator
import me.domino.fa2.data.fa.session.ChallengeAwareFaHtmlDataSource
import me.domino.fa2.data.fa.session.ChallengeCookiePolicy
import me.domino.fa2.data.fa.session.ChallengeProbeVerifier
import me.domino.fa2.data.fa.session.ChallengeResolver
import me.domino.fa2.data.fa.session.ChallengeSessionStorage
import me.domino.fa2.data.fa.session.CloudflareChallengeCookiePolicy
import me.domino.fa2.data.fa.session.CookiePersistence
import me.domino.fa2.data.fa.session.DefaultAuthSessionController
import me.domino.fa2.data.fa.session.FaCookiesStorage
import me.domino.fa2.data.fa.session.KSafeCookiePersistence
import me.domino.fa2.data.fa.session.PendingFaRouteStore
import me.domino.fa2.data.fa.session.UserAgentStorage
import me.domino.fa2.data.fa.social.SocialActionEndpoint
import me.domino.fa2.data.fa.submission.SubmissionDataSource
import me.domino.fa2.data.fa.submission.SubmissionEndpoint
import me.domino.fa2.data.fa.submission.SubmissionPageCache
import me.domino.fa2.data.fa.submission.SubmissionParser
import me.domino.fa2.data.fa.submission.SubmissionRepository
import me.domino.fa2.data.fa.user.ActivityHistoryRepository
import me.domino.fa2.data.fa.user.UserDataSource
import me.domino.fa2.data.fa.user.UserEndpoint
import me.domino.fa2.data.fa.user.UserPageCache
import me.domino.fa2.data.fa.user.UserParser
import me.domino.fa2.data.fa.user.UserRepository
import me.domino.fa2.data.fa.watchlist.WatchlistDataSource
import me.domino.fa2.data.fa.watchlist.WatchlistEndpoint
import me.domino.fa2.data.fa.watchlist.WatchlistPageCache
import me.domino.fa2.data.fa.watchlist.WatchlistParser
import me.domino.fa2.data.fa.watchlist.WatchlistRepository
import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.data.local.AppDatabaseBuilderFactory
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.local.settings.AppSettingsStorage
import me.domino.fa2.data.local.watchrecommendation.PersistedWatchRecommendationBlocklistRepository
import me.domino.fa2.data.local.watchrecommendation.WatchRecommendationBlocklist
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.AppSettingsStore
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.data.translation.KtorTranslationPort
import me.domino.fa2.data.translation.SubmissionTextTranslationEngine
import me.domino.fa2.data.translation.TranslationPort
import me.domino.fa2.domain.attachmenttext.AttachmentTextService
import me.domino.fa2.domain.ocr.DefaultSubmissionImageOcrService
import me.domino.fa2.domain.ocr.SubmissionImageOcrService
import me.domino.fa2.domain.submissionseries.SubmissionSeriesSubmissionSource
import me.domino.fa2.domain.watchrecommendation.WatchRecommendationUserSource
import me.domino.fa2.domain.watchrecommendation.WatchRecommendationWatchlistSource
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
    FaHttpClient(client = get(), cookiesStorage = get(), userAgentStorage = get())
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
  single {
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

  single { FeedPageCache(dataSource = get(), pageCacheDao = get()) }
  single { BrowsePageCache(dataSource = get(), pageCacheDao = get()) }
  single { SearchPageCache(dataSource = get(), pageCacheDao = get()) }
  single { SubmissionPageCache(dataSource = get(), pageCacheDao = get()) }
  single { UserPageCache(dataSource = get(), pageCacheDao = get()) }
  single {
    GalleryPageCache(galleryDataSource = get(), favoritesDataSource = get(), pageCacheDao = get())
  }
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
    DefaultSubmissionImageOcrService(
        imageBytesSource = get(),
        recognitionPort = get(),
    )
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
  single { UserRepository(get(), get()) } bind WatchRecommendationUserSource::class
  single { GalleryRepository(get()) }
  single { FavoritesRepository(get()) }
  single { JournalsRepository(get()) }
  single { JournalRepository(get()) }
  single { WatchlistRepository(get()) } bind WatchRecommendationWatchlistSource::class
}
