package me.domino.fa2.data.taxonomy

import kotlinx.serialization.Serializable

@Serializable
data class FaTaxonomyCatalog(
    val version: Int = 1,
    val sections: FaTaxonomySections,
)

@Serializable
data class FaTaxonomySections(
    val category: FaTaxonomySection,
    val type: FaTaxonomySection,
    val species: FaTaxonomySection,
)

@Serializable
data class FaTaxonomySection(
    val groups: List<FaTaxonomyGroup> = emptyList(),
    val items: Map<String, FaTaxonomyItem> = emptyMap(),
)

@Serializable
data class FaTaxonomyGroup(
    val key: String,
    val displayName: Map<String, String> = emptyMap(),
    val itemKeys: List<String> = emptyList(),
    val icon: String? = null,
)

@Serializable
data class FaTaxonomyItem(
    val id: Int,
    val displayName: Map<String, String> = emptyMap(),
    val groupKey: String? = null,
    val icon: String? = null,
)

enum class FaTaxonomySectionKey {
  CATEGORY,
  TYPE,
  SPECIES,
}
