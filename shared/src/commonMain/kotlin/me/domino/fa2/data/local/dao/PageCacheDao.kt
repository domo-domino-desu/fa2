package me.domino.fa2.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.domino.fa2.data.local.entity.PageCacheEntity

/**
 * 页面缓存访问接口。
 */
@Dao
interface PageCacheDao {
    /**
     * 观察指定缓存键。
     * @param key 缓存键。
     */
    @Query("SELECT * FROM fa_page_cache WHERE cacheKey = :key LIMIT 1")
    fun observeByKey(key: String): Flow<PageCacheEntity?>

    /**
     * 同步读取指定缓存键。
     * @param key 缓存键。
     */
    @Query("SELECT * FROM fa_page_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun findByKey(key: String): PageCacheEntity?

    /**
     * 读取指定页面类型下全部缓存。
     * @param pageType 页面类型。
     */
    @Query("SELECT * FROM fa_page_cache WHERE pageType = :pageType ORDER BY cachedAtMs DESC")
    suspend fun listByPageType(pageType: String): List<PageCacheEntity>

    /**
     * 写入或更新缓存。
     * @param entity 缓存实体。
     */
    @Upsert
    suspend fun upsert(entity: PageCacheEntity)

    /**
     * 删除指定缓存。
     * @param key 缓存键。
     */
    @Query("DELETE FROM fa_page_cache WHERE cacheKey = :key")
    suspend fun delete(key: String)

    /**
     * 清空缓存表。
     */
    @Query("DELETE FROM fa_page_cache")
    suspend fun deleteAll()
}
