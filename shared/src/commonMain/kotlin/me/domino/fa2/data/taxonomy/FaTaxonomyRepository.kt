package me.domino.fa2.data.taxonomy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.i18n.AppLanguage
import me.domino.fa2.i18n.MetadataDisplayPreferences
import me.domino.fa2.i18n.defaultMetadataDisplayPreferences
import me.domino.fa2.i18n.localizedFor
import me.domino.fa2.i18n.localizedOrOriginal
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

  fun categoryOptions(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoice<Int>> = options(FaTaxonomySectionKey.CATEGORY, metadata)

  fun typeOptions(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoice<Int>> = options(FaTaxonomySectionKey.TYPE, metadata)

  fun speciesOptions(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoice<Int>> = options(FaTaxonomySectionKey.SPECIES, metadata)

  fun categoryOptionGroups(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoiceGroup<Int>> = optionGroups(FaTaxonomySectionKey.CATEGORY, metadata)

  fun typeOptionGroups(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoiceGroup<Int>> = optionGroups(FaTaxonomySectionKey.TYPE, metadata)

  fun speciesOptionGroups(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoiceGroup<Int>> = optionGroups(FaTaxonomySectionKey.SPECIES, metadata)

  fun categoryDisplayNameById(
      id: Int,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameById(FaTaxonomySectionKey.CATEGORY, id, metadata)

  fun typeDisplayNameById(
      id: Int,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameById(FaTaxonomySectionKey.TYPE, id, metadata)

  fun speciesDisplayNameById(
      id: Int,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameById(FaTaxonomySectionKey.SPECIES, id, metadata)

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

  fun categoryDisplayNameByEnglishLabel(
      label: String,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameByEnglishLabel(FaTaxonomySectionKey.CATEGORY, label, metadata)

  fun typeDisplayNameByEnglishLabel(
      label: String,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameByEnglishLabel(FaTaxonomySectionKey.TYPE, label, metadata)

  fun speciesDisplayNameByEnglishLabel(
      label: String,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameByEnglishLabel(FaTaxonomySectionKey.SPECIES, label, metadata)

  private fun options(
      sectionKey: FaTaxonomySectionKey,
      metadata: MetadataDisplayPreferences,
  ): List<FaTaxonomyChoice<Int>> =
      preparedCatalog?.section(sectionKey)?.choicesFor(metadata).orEmpty()

  private fun optionGroups(
      sectionKey: FaTaxonomySectionKey,
      metadata: MetadataDisplayPreferences,
  ): List<FaTaxonomyChoiceGroup<Int>> =
      preparedCatalog?.section(sectionKey)?.choiceGroupsFor(metadata).orEmpty()

  private fun displayNameById(
      sectionKey: FaTaxonomySectionKey,
      id: Int,
      metadata: MetadataDisplayPreferences,
  ): String? =
      preparedCatalog?.section(sectionKey)?.itemsById?.get(id)?.item?.displayName?.let { display ->
        metadata.localizedOrOriginal(display, original = display.originalEnglishLabel())
      }

  private fun cardIconById(sectionKey: FaTaxonomySectionKey, id: Int): String? =
      preparedCatalog?.section(sectionKey)?.itemsById?.get(id)?.icon

  private fun groupById(sectionKey: FaTaxonomySectionKey, id: Int): FaTaxonomyGroup? =
      preparedCatalog?.section(sectionKey)?.itemsById?.get(id)?.group

  private fun findIdByEnglishLabel(sectionKey: FaTaxonomySectionKey, label: String): Int? =
      preparedCatalog?.section(sectionKey)?.englishLabelToId?.get(normalizeEnglishLabel(label))

  private fun displayNameByEnglishLabel(
      sectionKey: FaTaxonomySectionKey,
      label: String,
      metadata: MetadataDisplayPreferences,
  ): String? {
    val id = findIdByEnglishLabel(sectionKey, label) ?: return null
    return displayNameById(sectionKey, id, metadata)
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
    val optionGroups: List<PreparedFaTaxonomyChoiceGroup>,
    val itemsById: Map<Int, PreparedFaTaxonomyEntry>,
    val itemsByKey: Map<String, PreparedFaTaxonomyEntry>,
    val englishLabelToId: Map<String, Int>,
) {
  fun choicesFor(metadata: MetadataDisplayPreferences): List<FaTaxonomyChoice<Int>> =
      optionGroups.flatMap { group -> group.asChoiceGroup(metadata).options }

  fun choiceGroupsFor(metadata: MetadataDisplayPreferences): List<FaTaxonomyChoiceGroup<Int>> =
      optionGroups.map { group -> group.asChoiceGroup(metadata) }

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
                  englishLabelToId[normalizeEnglishLabel(item.displayName.originalEnglishLabel())] =
                      item.id
                  PreparedFaTaxonomyChoice(
                      value = item.id,
                      originalLabel = item.displayName.originalEnglishLabel(),
                      localized = item.displayName,
                  )
                }
            if (options.isEmpty()) {
              null
            } else {
              PreparedFaTaxonomyChoiceGroup(
                  originalLabel = group.displayName.originalEnglishLabel(),
                  localized = group.displayName,
                  options = options,
              )
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
        englishLabelToId[normalizeEnglishLabel(item.displayName.originalEnglishLabel())] = item.id
      }

      return PreparedFaTaxonomySection(
          optionGroups = optionGroups,
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

private data class PreparedFaTaxonomyChoice(
    val value: Int,
    val originalLabel: String,
    val localized: Map<String, String>,
) {
  fun asChoice(metadata: MetadataDisplayPreferences): FaTaxonomyChoice<Int> =
      FaTaxonomyChoice(
          value = value,
          label = metadata.localizedOrOriginal(localized, originalLabel),
      )
}

private data class PreparedFaTaxonomyChoiceGroup(
    val originalLabel: String,
    val localized: Map<String, String>,
    val options: List<PreparedFaTaxonomyChoice>,
) {
  fun asChoiceGroup(metadata: MetadataDisplayPreferences): FaTaxonomyChoiceGroup<Int> =
      FaTaxonomyChoiceGroup(
          label = metadata.localizedOrOriginal(localized, originalLabel),
          options = options.map { option -> option.asChoice(metadata) },
      )
}

private fun normalizeEnglishLabel(label: String): String =
    label.trim().lowercase().replace(Regex("\\s+"), " ")

private fun Map<String, String>.originalEnglishLabel(): String = localizedFor(AppLanguage.EN)
