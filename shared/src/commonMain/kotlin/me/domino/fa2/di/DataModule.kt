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
import me.domino.fa2.data.remote.fa.browse.BrowseRepository
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.favorites.FavoritesRepository
import me.domino.fa2.data.remote.fa.feed.FeedParser
import me.domino.fa2.data.remote.fa.feed.FeedRepository
import me.domino.fa2.data.remote.fa.gallery.GalleryRepository
import me.domino.fa2.data.remote.fa.journal.JournalParser
import me.domino.fa2.data.remote.fa.journal.JournalRepository
import me.domino.fa2.data.remote.fa.journal.JournalsParser
import me.domino.fa2.data.remote.fa.journal.JournalsRepository
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser
import me.domino.fa2.data.remote.fa.media.AttachmentDownloadEndpoint
import me.domino.fa2.data.remote.fa.media.AttachmentDownloadSource
import me.domino.fa2.data.remote.fa.media.ImageProgressTracker
import me.domino.fa2.data.remote.fa.media.KtorImageBytesSource
import me.domino.fa2.data.remote.fa.media.createCachedDownloadHttpClient
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
import me.domino.fa2.data.remote.fa.submission.SubmissionParser
import me.domino.fa2.data.remote.fa.submission.SubmissionRepository
import me.domino.fa2.data.remote.fa.submission.SubmissionSeriesSubmissionSource
import me.domino.fa2.data.remote.fa.user.ActivityHistoryRepository
import me.domino.fa2.data.remote.fa.user.UserParser
import me.domino.fa2.data.remote.fa.user.UserRepository
import me.domino.fa2.data.remote.fa.user.WatchRecommendationUserSource
import me.domino.fa2.data.remote.fa.watchlist.WatchRecommendationWatchlistSource
import me.domino.fa2.data.remote.fa.watchlist.WatchlistParser
import me.domino.fa2.data.remote.fa.watchlist.WatchlistRepository
import me.domino.fa2.data.remote.translation.KtorTranslationPort
import me.domino.fa2.data.remote.translation.SubmissionTextTranslationEngine
import me.domino.fa2.data.remote.translation.TranslationPort
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.AppSettingsStore
import me.domino.fa2.domain.attachmenttext.AttachmentTextService
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Data layer and infrastructure dependencies. */
fun dataModule(): Module = module {
  single<AppDatabase> { get<AppDatabaseBuilderFactory>().create().build() }
  single { get<AppDatabase>().pageCacheDao() }
  single { PageQueryCache(get()) }
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
  single { SubmissionTextTranslationEngine(get(), get()) }
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
  single {
    KtorImageBytesSource(client = get(qualifier = named(KOIN_QUALIFIER_CACHED_DOWNLOAD_CLIENT)))
  }

  single { HomeEndpoint(get()) }
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

  single<AppSettingsStore> {
    AppSettingsStorage(
        kv = get(),
        secretVault = get<KSafe>(qualifier = named(KOIN_QUALIFIER_SETTINGS_SECRET_VAULT)),
    )
  }
  single { AppSettingsService(get()) }
  single { FaTaxonomyRepository() }
  single { AttachmentTextService(get()) }
  single { ActivityHistoryRepository(get()) }
  single { AuthRepository(get()) }
  single { AuthSessionProfileStore(get()) }
  single { FeedRepository(get(), get(), get()) }
  single { BrowseRepository(get(), get(), get()) }
  single { SearchRepository(get(), get(), get()) }
  single { SubmissionRepository(get(), get(), get(), get()) } bind
      SubmissionSeriesSubmissionSource::class
  single { PersistedWatchRecommendationBlocklistRepository(get()) } bind
      WatchRecommendationBlocklist::class
  single { UserRepository(get(), get(), get(), get()) }
  single<WatchRecommendationUserSource> { get<UserRepository>() }
  single { GalleryRepository(get(), get(), get()) }
  single { FavoritesRepository(get(), get(), get()) }
  single { JournalsRepository(get(), get(), get()) }
  single { JournalRepository(get(), get(), get()) }
  single { WatchlistRepository(get(), get(), get()) } bind WatchRecommendationWatchlistSource::class
}
