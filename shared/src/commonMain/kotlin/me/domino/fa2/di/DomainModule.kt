package me.domino.fa2.di

import me.domino.fa2.data.local.watchrecommendation.WatchRecommendationBlocklist
import me.domino.fa2.domain.submissionseries.SubmissionSeriesService
import me.domino.fa2.domain.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.domain.translation.SubmissionImageOcrTranslationService
import me.domino.fa2.domain.watchrecommendation.WatchRecommendationService
import me.domino.fa2.domain.watchrecommendation.WatchRecommendationUserSource
import me.domino.fa2.domain.watchrecommendation.WatchRecommendationWatchlistSource
import org.koin.core.module.Module
import org.koin.dsl.module

/** Domain service 依赖模块。 */
fun domainModule(): Module = module {
  single { SubmissionDescriptionTranslationService(get(), get()) }
  single { SubmissionImageOcrTranslationService(get(), get()) }
  single { SubmissionSeriesService(get()) }
  single {
    WatchRecommendationService(
        get<WatchRecommendationWatchlistSource>(),
        get<WatchRecommendationUserSource>(),
        get<WatchRecommendationBlocklist>(),
    )
  }
}
