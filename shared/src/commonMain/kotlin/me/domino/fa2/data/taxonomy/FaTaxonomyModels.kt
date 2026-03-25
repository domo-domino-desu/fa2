package me.domino.fa2.data.taxonomy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocalizedDisplayName(
    val en: String = "",
    @SerialName("zh-Hans") val zhHans: String = "",
) {
  fun preferred(): String = zhHans.ifBlank { en }
}

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
    val displayName: LocalizedDisplayName,
    val itemKeys: List<String> = emptyList(),
    val icon: String? = null,
)

@Serializable
data class FaTaxonomyItem(
    val id: Int,
    val displayName: LocalizedDisplayName,
    val groupKey: String? = null,
    val icon: String? = null,
)

enum class FaTaxonomySectionKey {
  CATEGORY,
  TYPE,
  SPECIES,
}
