package me.domino.fa2.di

import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import me.domino.fa2.app.challenge.CfChallengeController
import me.domino.fa2.app.challenge.CfChallengeCoordinator
import me.domino.fa2.app.challenge.ChallengeCookiePolicy
import me.domino.fa2.app.challenge.ChallengeProbeVerifier
import me.domino.fa2.app.challenge.ChallengeSessionStore
import me.domino.fa2.app.challenge.CloudflareChallengeCookiePolicy
import me.domino.fa2.data.network.CookiePersistence
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.FaHttpClient
import me.domino.fa2.data.network.ImageProgressTracker
import me.domino.fa2.data.network.KSafeCookiePersistence
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.network.challenge.ChallengeAwareFaHtmlDataSource
import me.domino.fa2.data.network.challenge.ChallengeResolver
import me.domino.fa2.data.network.endpoint.AttachmentDownloadEndpoint
import me.domino.fa2.data.network.endpoint.AttachmentDownloadSource
import me.domino.fa2.data.network.endpoint.BrowseEndpoint
import me.domino.fa2.data.network.endpoint.FavoritesEndpoint
import me.domino.fa2.data.network.endpoint.FeedEndpoint
import me.domino.fa2.data.network.endpoint.GalleryEndpoint
import me.domino.fa2.data.network.endpoint.HomeEndpoint
import me.domino.fa2.data.network.endpoint.JournalEndpoint
import me.domino.fa2.data.network.endpoint.JournalsEndpoint
import me.domino.fa2.data.network.endpoint.SearchEndpoint
import me.domino.fa2.data.network.endpoint.SocialActionEndpoint
import me.domino.fa2.data.network.endpoint.SubmissionEndpoint
import me.domino.fa2.data.network.endpoint.UserEndpoint
import me.domino.fa2.data.network.endpoint.WatchlistEndpoint
import me.domino.fa2.data.translation.KtorTranslationPort
import me.domino.fa2.data.translation.TranslationPort
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** 网络相关依赖模块。 */
fun networkModule(): Module = module {
  single<CookiePersistence> {
    KSafeCookiePersistence(cookieVault = get<KSafe>(qualifier = named(KOIN_QUALIFIER_COOKIE_VAULT)))
  }
  single { FaCookiesStorage(get()) }
  single { UserAgentStorage(get()) }
  single { ImageProgressTracker() }

  single<HttpClient> { HttpClient { expectSuccess = false } }
  single<TranslationPort> { KtorTranslationPort(get()) }
  single<FaHtmlDataSource>(qualifier = named(KOIN_QUALIFIER_RAW_HTML_DATA_SOURCE)) {
    FaHttpClient(client = get(), cookiesStorage = get(), userAgentStorage = get())
  }
  single<ChallengeCookiePolicy> { CloudflareChallengeCookiePolicy() }
  single { ChallengeSessionStore() }
  single {
    ChallengeProbeVerifier(
        rawHtmlDataSource = get(qualifier = named(KOIN_QUALIFIER_RAW_HTML_DATA_SOURCE))
    )
  }
  single {
    CfChallengeCoordinator(
        sessionStore = get(),
        cookiesStorage = get(),
        userAgentStorage = get(),
        cookiePolicy = get(),
        probeVerifier = get(),
    )
  }
  single<CfChallengeController> { get<CfChallengeCoordinator>() }
  single<ChallengeResolver> { get<CfChallengeCoordinator>() }
  single<FaHtmlDataSource> {
    ChallengeAwareFaHtmlDataSource(
        delegate = get(qualifier = named(KOIN_QUALIFIER_RAW_HTML_DATA_SOURCE)),
        challengeResolver = get(),
    )
  }
  single<HttpClient>(qualifier = named(KOIN_QUALIFIER_SOCIAL_ACTION_CLIENT)) {
    get<HttpClient>().config { followRedirects = false }
  }
  single<AttachmentDownloadSource> {
    AttachmentDownloadEndpoint(
        client = get(),
        cookiesStorage = get(),
        userAgentStorage = get(),
        challengeResolver = get(),
    )
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
        challengeResolver = get(),
    )
  }
}
