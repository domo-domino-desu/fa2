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

/** FA 分类目录仓库，负责加载和查询分类、类型、物种等元数据。 */
class FaTaxonomyRepository {
  /** 日志标签。 */
  private val log = FaLog.withTag("FaTaxonomyRepository")
  /** 保护并发加载的互斥锁。 */
  private val loadMutex = Mutex()
  /** 可变的分类目录状态流。 */
  private val mutableCatalog = MutableStateFlow<FaTaxonomyCatalog?>(null)

  /** 已解析并索引好的分类目录，加载完成后才非 null。 */
  @Volatile private var preparedCatalog: PreparedFaTaxonomyCatalog? = null

  /** 对外暴露的只读分类目录状态流。 */
  val catalog: StateFlow<FaTaxonomyCatalog?> = mutableCatalog.asStateFlow()

  /** 若目录尚未加载则触发加载，已加载时直接返回。 */
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

  /** 返回分类（Category）的扁平选项列表。 */
  fun categoryOptions(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoice<Int>> = options(FaTaxonomySectionKey.CATEGORY, metadata)

  /** 返回类型（Type）的扁平选项列表。 */
  fun typeOptions(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoice<Int>> = options(FaTaxonomySectionKey.TYPE, metadata)

  /** 返回物种（Species）的扁平选项列表。 */
  fun speciesOptions(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoice<Int>> = options(FaTaxonomySectionKey.SPECIES, metadata)

  /** 返回分类（Category）的分组选项列表。 */
  fun categoryOptionGroups(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoiceGroup<Int>> = optionGroups(FaTaxonomySectionKey.CATEGORY, metadata)

  /** 返回类型（Type）的分组选项列表。 */
  fun typeOptionGroups(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoiceGroup<Int>> = optionGroups(FaTaxonomySectionKey.TYPE, metadata)

  /** 返回物种（Species）的分组选项列表。 */
  fun speciesOptionGroups(
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences
  ): List<FaTaxonomyChoiceGroup<Int>> = optionGroups(FaTaxonomySectionKey.SPECIES, metadata)

  /** 按 ID 查询分类（Category）的展示名称。 */
  fun categoryDisplayNameById(
      id: Int,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameById(FaTaxonomySectionKey.CATEGORY, id, metadata)

  /** 按 ID 查询类型（Type）的展示名称。 */
  fun typeDisplayNameById(
      id: Int,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameById(FaTaxonomySectionKey.TYPE, id, metadata)

  /** 按 ID 查询物种（Species）的展示名称。 */
  fun speciesDisplayNameById(
      id: Int,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameById(FaTaxonomySectionKey.SPECIES, id, metadata)

  /** 按 ID 查询分类（Category）的卡片图标。 */
  fun categoryCardIconById(id: Int): String? = cardIconById(FaTaxonomySectionKey.CATEGORY, id)

  /** 按 tag 字符串查询分类（Category）的卡片图标。 */
  fun categoryCardIconByTag(tag: String): String? =
      preparedCatalog?.category?.itemsByKey?.get(tag.trim().lowercase())?.icon

  /** 按 ID 查询分类（Category）所属分组。 */
  fun categoryGroupById(id: Int): FaTaxonomyGroup? = groupById(FaTaxonomySectionKey.CATEGORY, id)

  /** 按 ID 查询类型（Type）所属分组。 */
  fun typeGroupById(id: Int): FaTaxonomyGroup? = groupById(FaTaxonomySectionKey.TYPE, id)

  /** 按 ID 查询物种（Species）所属分组。 */
  fun speciesGroupById(id: Int): FaTaxonomyGroup? = groupById(FaTaxonomySectionKey.SPECIES, id)

  /** 按英文标签查找分类（Category）的 ID。 */
  fun findCategoryIdByEnglishLabel(label: String): Int? =
      findIdByEnglishLabel(FaTaxonomySectionKey.CATEGORY, label)

  /** 按英文标签查找类型（Type）的 ID。 */
  fun findTypeIdByEnglishLabel(label: String): Int? =
      findIdByEnglishLabel(FaTaxonomySectionKey.TYPE, label)

  /** 按英文标签查找物种（Species）的 ID。 */
  fun findSpeciesIdByEnglishLabel(label: String): Int? =
      findIdByEnglishLabel(FaTaxonomySectionKey.SPECIES, label)

  /** 按英文标签查询分类（Category）的展示名称。 */
  fun categoryDisplayNameByEnglishLabel(
      label: String,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameByEnglishLabel(FaTaxonomySectionKey.CATEGORY, label, metadata)

  /** 按英文标签查询类型（Type）的展示名称。 */
  fun typeDisplayNameByEnglishLabel(
      label: String,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameByEnglishLabel(FaTaxonomySectionKey.TYPE, label, metadata)

  /** 按英文标签查询物种（Species）的展示名称。 */
  fun speciesDisplayNameByEnglishLabel(
      label: String,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String? = displayNameByEnglishLabel(FaTaxonomySectionKey.SPECIES, label, metadata)

  /** 按节键和展示偏好返回扁平选项列表。 */
  private fun options(
      sectionKey: FaTaxonomySectionKey,
      metadata: MetadataDisplayPreferences,
  ): List<FaTaxonomyChoice<Int>> =
      preparedCatalog?.section(sectionKey)?.choicesFor(metadata).orEmpty()

  /** 按节键和展示偏好返回分组选项列表。 */
  private fun optionGroups(
      sectionKey: FaTaxonomySectionKey,
      metadata: MetadataDisplayPreferences,
  ): List<FaTaxonomyChoiceGroup<Int>> =
      preparedCatalog?.section(sectionKey)?.choiceGroupsFor(metadata).orEmpty()

  /** 按节键和 ID 查询展示名称。 */
  private fun displayNameById(
      sectionKey: FaTaxonomySectionKey,
      id: Int,
      metadata: MetadataDisplayPreferences,
  ): String? =
      preparedCatalog?.section(sectionKey)?.itemsById?.get(id)?.item?.displayName?.let { display ->
        metadata.localizedOrOriginal(display, original = display.originalEnglishLabel())
      }

  /** 按节键和 ID 查询卡片图标。 */
  private fun cardIconById(sectionKey: FaTaxonomySectionKey, id: Int): String? =
      preparedCatalog?.section(sectionKey)?.itemsById?.get(id)?.icon

  /** 按节键和 ID 查询所属分组。 */
  private fun groupById(sectionKey: FaTaxonomySectionKey, id: Int): FaTaxonomyGroup? =
      preparedCatalog?.section(sectionKey)?.itemsById?.get(id)?.group

  /** 按节键和英文标签查找对应 ID。 */
  private fun findIdByEnglishLabel(sectionKey: FaTaxonomySectionKey, label: String): Int? =
      preparedCatalog?.section(sectionKey)?.englishLabelToId?.get(normalizeEnglishLabel(label))

  /** 按节键和英文标签查询展示名称。 */
  private fun displayNameByEnglishLabel(
      sectionKey: FaTaxonomySectionKey,
      label: String,
      metadata: MetadataDisplayPreferences,
  ): String? {
    val id = findIdByEnglishLabel(sectionKey, label) ?: return null
    return displayNameById(sectionKey, id, metadata)
  }
}

/** 已预处理并索引的完整分类目录，包含三个节的数据。 */
private data class PreparedFaTaxonomyCatalog(
    /** 分类（Category）节。 */
    val category: PreparedFaTaxonomySection,
    /** 类型（Type）节。 */
    val type: PreparedFaTaxonomySection,
    /** 物种（Species）节。 */
    val species: PreparedFaTaxonomySection,
) {
  /** 按节键返回对应的预处理节数据。 */
  fun section(sectionKey: FaTaxonomySectionKey): PreparedFaTaxonomySection =
      when (sectionKey) {
        FaTaxonomySectionKey.CATEGORY -> category
        FaTaxonomySectionKey.TYPE -> type
        FaTaxonomySectionKey.SPECIES -> species
      }

  companion object {
    /** 从原始目录数据构建预处理目录。 */
    fun from(catalog: FaTaxonomyCatalog): PreparedFaTaxonomyCatalog =
        PreparedFaTaxonomyCatalog(
            category = PreparedFaTaxonomySection.from(catalog.sections.category),
            type = PreparedFaTaxonomySection.from(catalog.sections.type),
            species = PreparedFaTaxonomySection.from(catalog.sections.species),
        )
  }
}

/** 已预处理并索引的单个分类节，支持按 ID、按 Key 及按英文标签快速查找。 */
private data class PreparedFaTaxonomySection(
    /** 按分组排列的选项列表。 */
    val optionGroups: List<PreparedFaTaxonomyChoiceGroup>,
    /** 按 ID 索引的条目映射。 */
    val itemsById: Map<Int, PreparedFaTaxonomyEntry>,
    /** 按 Key 索引的条目映射。 */
    val itemsByKey: Map<String, PreparedFaTaxonomyEntry>,
    /** 规范化英文标签到 ID 的映射。 */
    val englishLabelToId: Map<String, Int>,
) {
  /** 返回按展示偏好本地化后的扁平选项列表。 */
  fun choicesFor(metadata: MetadataDisplayPreferences): List<FaTaxonomyChoice<Int>> =
      optionGroups.flatMap { group -> group.asChoiceGroup(metadata).options }

  /** 返回按展示偏好本地化后的分组选项列表。 */
  fun choiceGroupsFor(metadata: MetadataDisplayPreferences): List<FaTaxonomyChoiceGroup<Int>> =
      optionGroups.map { group -> group.asChoiceGroup(metadata) }

  companion object {
    /** 从原始分类节数据构建预处理节，建立所有索引。 */
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

/** 预处理后的单个分类条目，含原始数据、所属分组及图标。 */
private data class PreparedFaTaxonomyEntry(
    /** 条目的 Key。 */
    val key: String,
    /** 原始分类条目数据。 */
    val item: FaTaxonomyItem,
    /** 所属分组，可能为 null。 */
    val group: FaTaxonomyGroup?,
    /** 卡片图标，优先取条目自身，其次取分组图标。 */
    val icon: String?,
)

/** 预处理后的单个选项，包含原始英文标签和多语言映射。 */
private data class PreparedFaTaxonomyChoice(
    /** 选项值（条目 ID）。 */
    val value: Int,
    /** 原始英文标签。 */
    val originalLabel: String,
    /** 多语言展示名称映射。 */
    val localized: Map<String, String>,
) {
  /** 按展示偏好返回本地化的 FaTaxonomyChoice。 */
  fun asChoice(metadata: MetadataDisplayPreferences): FaTaxonomyChoice<Int> =
      FaTaxonomyChoice(
          value = value,
          label = metadata.localizedOrOriginal(localized, originalLabel),
      )
}

/** 预处理后的选项分组，包含原始英文标签、多语言映射及子选项列表。 */
private data class PreparedFaTaxonomyChoiceGroup(
    /** 原始英文分组名。 */
    val originalLabel: String,
    /** 多语言分组名映射。 */
    val localized: Map<String, String>,
    /** 分组内的预处理选项列表。 */
    val options: List<PreparedFaTaxonomyChoice>,
) {
  /** 按展示偏好返回本地化的 FaTaxonomyChoiceGroup。 */
  fun asChoiceGroup(metadata: MetadataDisplayPreferences): FaTaxonomyChoiceGroup<Int> =
      FaTaxonomyChoiceGroup(
          label = metadata.localizedOrOriginal(localized, originalLabel),
          options = options.map { option -> option.asChoice(metadata) },
      )
}

/** 规范化英文标签，用于建立和查找英文标签索引。 */
private fun normalizeEnglishLabel(label: String): String =
    label.trim().lowercase().replace(Regex("\\s+"), " ")

/** 从多语言映射中取出英文原始标签。 */
private fun Map<String, String>.originalEnglishLabel(): String = localizedFor(AppLanguage.EN)
