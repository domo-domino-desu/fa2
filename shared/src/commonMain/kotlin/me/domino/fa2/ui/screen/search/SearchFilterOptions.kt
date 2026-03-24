package me.domino.fa2.ui.screen.search

import me.domino.fa2.ui.component.FaBrowseTaxonomyOptions
import me.domino.fa2.ui.component.FilterOption

internal val searchCategoryOptions = FaBrowseTaxonomyOptions.categoryOptions

internal val searchTypeOptions = FaBrowseTaxonomyOptions.typeOptions

internal val searchSpeciesOptions = FaBrowseTaxonomyOptions.speciesOptions

internal val searchTypeOptionGroups = FaBrowseTaxonomyOptions.typeOptionGroups

internal val searchSpeciesOptionGroups = FaBrowseTaxonomyOptions.speciesOptionGroups

internal val orderByOptions =
  listOf(
    FilterOption("relevancy", "Relevancy"),
    FilterOption("date", "Date"),
    FilterOption("popularity", "Popularity"),
  )

internal val orderDirectionOptions =
  listOf(FilterOption("desc", "Descending"), FilterOption("asc", "Ascending"))

internal val rangeOptions =
  listOf(
    FilterOption("all", "All Time"),
    FilterOption("1day", "Last 24 Hours"),
    FilterOption("3days", "Last 3 Days"),
    FilterOption("7days", "Last 7 Days"),
    FilterOption("30days", "Last 30 Days"),
    FilterOption("manual", "Manual"),
  )
