package me.domino.fa2.ui.pages.search

import me.domino.fa2.data.search.SearchUiLabelsRepository
import me.domino.fa2.ui.components.FilterOption

internal fun orderByOptions(labelsRepository: SearchUiLabelsRepository) =
    listOf(
        FilterOption("relevancy", labelsRepository.orderByLabel("relevancy")),
        FilterOption("date", labelsRepository.orderByLabel("date")),
        FilterOption("popularity", labelsRepository.orderByLabel("popularity")),
    )

internal fun orderDirectionOptions(labelsRepository: SearchUiLabelsRepository) =
    listOf(
        FilterOption("desc", labelsRepository.orderDirectionLabel("desc")),
        FilterOption("asc", labelsRepository.orderDirectionLabel("asc")),
    )

internal fun rangeOptions(labelsRepository: SearchUiLabelsRepository) =
    listOf(
        FilterOption("1day", labelsRepository.rangeLabel("1day")),
        FilterOption("3days", labelsRepository.rangeLabel("3days")),
        FilterOption("7days", labelsRepository.rangeLabel("7days")),
        FilterOption("30days", labelsRepository.rangeLabel("30days")),
        FilterOption("90days", labelsRepository.rangeLabel("90days")),
        FilterOption("1year", labelsRepository.rangeLabel("1year")),
        FilterOption("3years", labelsRepository.rangeLabel("3years")),
        FilterOption("5years", labelsRepository.rangeLabel("5years")),
        FilterOption("all", labelsRepository.rangeLabel("all")),
        FilterOption("manual", labelsRepository.rangeLabel("manual")),
    )
