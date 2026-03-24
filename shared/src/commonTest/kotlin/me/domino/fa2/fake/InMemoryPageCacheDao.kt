package me.domino.fa2.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity

/**
 * 测试用内存 PageCacheDao。
 */
class InMemoryPageCacheDao : PageCacheDao {
    private val entitiesFlow = MutableStateFlow<Map<String, PageCacheEntity>>(emptyMap())
    private val entitiesByKey: MutableMap<String, PageCacheEntity> = mutableMapOf()

    override fun observeByKey(key: String): Flow<PageCacheEntity?> =
        entitiesFlow.map { map -> map[key] }

    override suspend fun findByKey(key: String): PageCacheEntity? = entitiesByKey[key]

    override suspend fun listByPageType(pageType: String): List<PageCacheEntity> =
        entitiesByKey.values
            .filter { entity -> entity.pageType == pageType }
            .sortedByDescending { entity -> entity.cachedAtMs }

    override suspend fun upsert(entity: PageCacheEntity) {
        entitiesByKey[entity.cacheKey] = entity
        entitiesFlow.value = entitiesByKey.toMap()
    }

    override suspend fun delete(key: String) {
        entitiesByKey.remove(key)
        entitiesFlow.value = entitiesByKey.toMap()
    }

    override suspend fun deleteAll() {
        entitiesByKey.clear()
        entitiesFlow.value = emptyMap()
    }
}
