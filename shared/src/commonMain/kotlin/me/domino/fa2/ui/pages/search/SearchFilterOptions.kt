package me.domino.fa2.ui.pages.search

import me.domino.fa2.i18n.AppLanguage
import me.domino.fa2.ui.components.FilterOption
import me.domino.fa2.ui.search.SearchUiLabelsRepository
import me.domino.fa2.ui.search.SearchUiOptionKey

internal fun orderByOptions(
    labelsRepository: SearchUiLabelsRepository,
    language: AppLanguage,
) =
    listOf(
        FilterOption(
            "relevancy",
            labelsRepository.optionLabel(SearchUiOptionKey.ORDER_BY, "relevancy", language),
        ),
        FilterOption(
            "date",
            labelsRepository.optionLabel(SearchUiOptionKey.ORDER_BY, "date", language),
        ),
        FilterOption(
            "popularity",
            labelsRepository.optionLabel(SearchUiOptionKey.ORDER_BY, "popularity", language),
        ),
    )

internal fun orderDirectionOptions(
    labelsRepository: SearchUiLabelsRepository,
    language: AppLanguage,
) =
    listOf(
        FilterOption(
            "desc",
            labelsRepository.optionLabel(SearchUiOptionKey.ORDER_DIRECTION, "desc", language),
        ),
        FilterOption(
            "asc",
            labelsRepository.optionLabel(SearchUiOptionKey.ORDER_DIRECTION, "asc", language),
        ),
    )

internal fun rangeOptions(
    labelsRepository: SearchUiLabelsRepository,
    language: AppLanguage,
) =
    listOf(
        FilterOption(
            "1day",
            labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "1day", language),
        ),
        FilterOption(
            "3days",
            labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "3days", language),
        ),
        FilterOption(
            "7days",
            labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "7days", language),
        ),
        FilterOption(
            "30days",
            labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "30days", language),
        ),
        FilterOption(
            "90days",
            labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "90days", language),
        ),
        FilterOption(
            "1year",
            labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "1year", language),
        ),
        FilterOption(
            "3years",
            labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "3years", language),
        ),
        FilterOption(
            "5years",
            labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "5years", language),
        ),
        FilterOption("all", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "all", language)),
        FilterOption(
            "manual",
            labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "manual", language),
        ),
    )
