package me.domino.fa2.ui.pages.search

import me.domino.fa2.data.search.SearchUiLabelsRepository
import me.domino.fa2.data.search.SearchUiOptionKey
import me.domino.fa2.ui.components.FilterOption

internal fun orderByOptions(labelsRepository: SearchUiLabelsRepository) =
    listOf(
        FilterOption(
            "relevancy",
            labelsRepository.optionLabel(SearchUiOptionKey.ORDER_BY, "relevancy"),
        ),
        FilterOption("date", labelsRepository.optionLabel(SearchUiOptionKey.ORDER_BY, "date")),
        FilterOption(
            "popularity",
            labelsRepository.optionLabel(SearchUiOptionKey.ORDER_BY, "popularity"),
        ),
    )

internal fun orderDirectionOptions(labelsRepository: SearchUiLabelsRepository) =
    listOf(
        FilterOption(
            "desc",
            labelsRepository.optionLabel(SearchUiOptionKey.ORDER_DIRECTION, "desc"),
        ),
        FilterOption("asc", labelsRepository.optionLabel(SearchUiOptionKey.ORDER_DIRECTION, "asc")),
    )

internal fun rangeOptions(labelsRepository: SearchUiLabelsRepository) =
    listOf(
        FilterOption("1day", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "1day")),
        FilterOption("3days", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "3days")),
        FilterOption("7days", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "7days")),
        FilterOption("30days", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "30days")),
        FilterOption("90days", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "90days")),
        FilterOption("1year", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "1year")),
        FilterOption("3years", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "3years")),
        FilterOption("5years", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "5years")),
        FilterOption("all", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "all")),
        FilterOption("manual", labelsRepository.optionLabel(SearchUiOptionKey.RANGE, "manual")),
    )
