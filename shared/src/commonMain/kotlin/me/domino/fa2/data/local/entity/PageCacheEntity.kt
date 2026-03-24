package me.domino.fa2.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 页面缓存表实体。
 */
@Entity(tableName = "fa_page_cache")
data class PageCacheEntity(
    /** 缓存主键。 */
    @PrimaryKey val cacheKey: String,
    /** 页面类型标识。 */
    val pageType: String,
    /** 序列化数据。 */
    val dataJson: String,
    /** 缓存时间戳（毫秒）。 */
    val cachedAtMs: Long,
)
