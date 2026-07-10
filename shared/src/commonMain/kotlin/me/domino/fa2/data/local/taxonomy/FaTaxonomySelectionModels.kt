package me.domino.fa2.data.local.taxonomy

data class FaTaxonomyChoice<T>(val value: T, val label: String)

data class FaTaxonomyChoiceGroup<T>(val label: String, val options: List<FaTaxonomyChoice<T>>)
