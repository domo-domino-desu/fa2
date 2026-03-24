package me.domino.fa2.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import me.domino.fa2.data.local.dao.HistoryDao
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.local.entity.SearchHistoryEntity
import me.domino.fa2.data.local.entity.SubmissionHistoryEntity

/**
 * 应用数据库。
 */
@Database(
    entities = [
        PageCacheEntity::class,
        SubmissionHistoryEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * 提供页面缓存数据访问对象。
     */
    abstract fun pageCacheDao(): PageCacheDao

    /**
     * 提供历史数据访问对象。
     */
    abstract fun historyDao(): HistoryDao
}

/**
 * 平台数据库构造器工厂。
 */
fun interface AppDatabaseBuilderFactory {
    /**
     * 创建数据库构造器。
     */
    fun create(): RoomDatabase.Builder<AppDatabase>
}
