package me.domino.fa2.ui.components.forms

import me.domino.fa2.data.local.taxonomy.FaTaxonomyChoice
import me.domino.fa2.data.local.taxonomy.FaTaxonomyChoiceGroup

internal fun <T> FaTaxonomyChoice<T>.toFilterOption(): FilterOption<T> =
    FilterOption(value = value, label = label)

internal fun <T> FaTaxonomyChoiceGroup<T>.toFilterOptionGroup(): FilterOptionGroup<T> =
    FilterOptionGroup(label = label, options = options.map { option -> option.toFilterOption() })
