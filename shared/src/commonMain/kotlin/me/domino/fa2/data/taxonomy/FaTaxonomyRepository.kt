package me.domino.fa2.data.taxonomy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.ui.components.FilterOption
import me.domino.fa2.ui.components.FilterOptionGroup
import me.domino.fa2.util.logging.FaLog

class FaTaxonomyRepository {
  private val log = FaLog.withTag("FaTaxonomyRepository")
  private val loadMutex = Mutex()
  private val mutableCatalog = MutableStateFlow<FaTaxonomyCatalog?>(null)

  @Volatile private var preparedCatalog: PreparedFaTaxonomyCatalog? = null

  val catalog: StateFlow<FaTaxonomyCatalog?> = mutableCatalog.asStateFlow()

  suspend fun ensureLoaded() {
    if (preparedCatalog != null) return

    loadMutex.withLock {
      if (preparedCatalog != null) return

      runCatching { loadFaTaxonomyCatalog() }
          .onSuccess { loaded ->
            preparedCatalog = PreparedFaTaxonomyCatalog.from(loaded)
            mutableCatalog.value = loaded
            log.i { "taxonomy -> 加载成功(version=${loaded.version})" }
          }
          .onFailure { error -> log.e(error) { "taxonomy -> 加载失败" } }
    }
  }

  fun categoryOptions(): List<FilterOption<Int>> = options(FaTaxonomySectionKey.CATEGORY)

  fun typeOptions(): List<FilterOption<Int>> = options(FaTaxonomySectionKey.TYPE)

  fun speciesOptions(): List<FilterOption<Int>> = options(FaTaxonomySectionKey.SPECIES)

  fun categoryOptionGroups(): List<FilterOptionGroup<Int>> =
      optionGroups(FaTaxonomySectionKey.CATEGORY)

  fun typeOptionGroups(): List<FilterOptionGroup<Int>> = optionGroups(FaTaxonomySectionKey.TYPE)

  fun speciesOptionGroups(): List<FilterOptionGroup<Int>> =
      optionGroups(FaTaxonomySectionKey.SPECIES)

  fun categoryDisplayNameById(id: Int): String? = displayNameById(FaTaxonomySectionKey.CATEGORY, id)

  fun typeDisplayNameById(id: Int): String? = displayNameById(FaTaxonomySectionKey.TYPE, id)

  fun speciesDisplayNameById(id: Int): String? = displayNameById(FaTaxonomySectionKey.SPECIES, id)

  fun categoryCardIconById(id: Int): String? = cardIconById(FaTaxonomySectionKey.CATEGORY, id)

  fun categoryCardIconByTag(tag: String): String? =
      preparedCatalog?.category?.itemsByKey?.get(tag.trim().lowercase())?.icon

  fun categoryGroupById(id: Int): FaTaxonomyGroup? = groupById(FaTaxonomySectionKey.CATEGORY, id)

  fun typeGroupById(id: Int): FaTaxonomyGroup? = groupById(FaTaxonomySectionKey.TYPE, id)

  fun speciesGroupById(id: Int): FaTaxonomyGroup? = groupById(FaTaxonomySectionKey.SPECIES, id)

  fun findCategoryIdByEnglishLabel(label: String): Int? =
      findIdByEnglishLabel(FaTaxonomySectionKey.CATEGORY, label)

  fun findTypeIdByEnglishLabel(label: String): Int? =
      findIdByEnglishLabel(FaTaxonomySectionKey.TYPE, label)

  fun findSpeciesIdByEnglishLabel(label: String): Int? =
      findIdByEnglishLabel(FaTaxonomySectionKey.SPECIES, label)

  fun categoryDisplayNameByEnglishLabel(label: String): String? =
      displayNameByEnglishLabel(FaTaxonomySectionKey.CATEGORY, label)

  fun typeDisplayNameByEnglishLabel(label: String): String? =
      displayNameByEnglishLabel(FaTaxonomySectionKey.TYPE, label)

  fun speciesDisplayNameByEnglishLabel(label: String): String? =
      displayNameByEnglishLabel(FaTaxonomySectionKey.SPECIES, label)

  private fun options(sectionKey: FaTaxonomySectionKey): List<FilterOption<Int>> =
      preparedCatalog?.section(sectionKey)?.options.orEmpty()

  private fun optionGroups(sectionKey: FaTaxonomySectionKey): List<FilterOptionGroup<Int>> =
      preparedCatalog?.section(sectionKey)?.optionGroups.orEmpty()

  private fun displayNameById(sectionKey: FaTaxonomySectionKey, id: Int): String? =
      preparedCatalog?.section(sectionKey)?.itemsById?.get(id)?.item?.displayName?.preferred()

  private fun cardIconById(sectionKey: FaTaxonomySectionKey, id: Int): String? =
      preparedCatalog?.section(sectionKey)?.itemsById?.get(id)?.icon

  private fun groupById(sectionKey: FaTaxonomySectionKey, id: Int): FaTaxonomyGroup? =
      preparedCatalog?.section(sectionKey)?.itemsById?.get(id)?.group

  private fun findIdByEnglishLabel(sectionKey: FaTaxonomySectionKey, label: String): Int? =
      preparedCatalog?.section(sectionKey)?.englishLabelToId?.get(normalizeEnglishLabel(label))

  private fun displayNameByEnglishLabel(sectionKey: FaTaxonomySectionKey, label: String): String? {
    val id = findIdByEnglishLabel(sectionKey, label) ?: return null
    return displayNameById(sectionKey, id)
  }
}

private data class PreparedFaTaxonomyCatalog(
    val category: PreparedFaTaxonomySection,
    val type: PreparedFaTaxonomySection,
    val species: PreparedFaTaxonomySection,
) {
  fun section(sectionKey: FaTaxonomySectionKey): PreparedFaTaxonomySection =
      when (sectionKey) {
        FaTaxonomySectionKey.CATEGORY -> category
        FaTaxonomySectionKey.TYPE -> type
        FaTaxonomySectionKey.SPECIES -> species
      }

  companion object {
    fun from(catalog: FaTaxonomyCatalog): PreparedFaTaxonomyCatalog =
        PreparedFaTaxonomyCatalog(
            category = PreparedFaTaxonomySection.from(catalog.sections.category),
            type = PreparedFaTaxonomySection.from(catalog.sections.type),
            species = PreparedFaTaxonomySection.from(catalog.sections.species),
        )
  }
}

private data class PreparedFaTaxonomySection(
    val optionGroups: List<FilterOptionGroup<Int>>,
    val options: List<FilterOption<Int>>,
    val itemsById: Map<Int, PreparedFaTaxonomyEntry>,
    val itemsByKey: Map<String, PreparedFaTaxonomyEntry>,
    val englishLabelToId: Map<String, Int>,
) {
  companion object {
    fun from(section: FaTaxonomySection): PreparedFaTaxonomySection {
      val groupsByKey = section.groups.associateBy(FaTaxonomyGroup::key)
      val entriesById = LinkedHashMap<Int, PreparedFaTaxonomyEntry>()
      val entriesByKey = LinkedHashMap<String, PreparedFaTaxonomyEntry>()
      val englishLabelToId = LinkedHashMap<String, Int>()
      val optionGroups =
          section.groups.mapNotNull { group ->
            val options =
                group.itemKeys.mapNotNull { itemKey ->
                  val item = section.items[itemKey] ?: return@mapNotNull null
                  val resolvedGroup = item.groupKey?.let(groupsByKey::get)
                  val entry =
                      PreparedFaTaxonomyEntry(
                          key = itemKey,
                          item = item,
                          group = resolvedGroup,
                          icon = item.icon ?: resolvedGroup?.icon,
                      )
                  entriesById[item.id] = entry
                  entriesByKey[itemKey] = entry
                  englishLabelToId[normalizeEnglishLabel(item.displayName.en)] = item.id
                  FilterOption(value = item.id, label = item.displayName.preferred())
                }
            if (options.isEmpty()) {
              null
            } else {
              FilterOptionGroup(label = group.displayName.preferred(), options = options)
            }
          }

      section.items.forEach { (itemKey, item) ->
        if (itemKey in entriesByKey) return@forEach
        val resolvedGroup = item.groupKey?.let(groupsByKey::get)
        val entry =
            PreparedFaTaxonomyEntry(
                key = itemKey,
                item = item,
                group = resolvedGroup,
                icon = item.icon ?: resolvedGroup?.icon,
            )
        entriesById[item.id] = entry
        entriesByKey[itemKey] = entry
        englishLabelToId[normalizeEnglishLabel(item.displayName.en)] = item.id
      }

      return PreparedFaTaxonomySection(
          optionGroups = optionGroups,
          options = optionGroups.flatMap(FilterOptionGroup<Int>::options),
          itemsById = entriesById,
          itemsByKey = entriesByKey,
          englishLabelToId = englishLabelToId,
      )
    }
  }
}

private data class PreparedFaTaxonomyEntry(
    val key: String,
    val item: FaTaxonomyItem,
    val group: FaTaxonomyGroup?,
    val icon: String?,
)

private fun normalizeEnglishLabel(label: String): String =
    label.trim().lowercase().replace(Regex("\\s+"), " ")
