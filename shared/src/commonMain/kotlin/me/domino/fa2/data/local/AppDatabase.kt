package me.domino.fa2.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import me.domino.fa2.data.local.dao.HistoryDao
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.local.entity.SearchHistoryEntity
import me.domino.fa2.data.local.entity.SubmissionHistoryEntity

/** 应用数据库。 */
@Database(
    entities = [PageCacheEntity::class, SubmissionHistoryEntity::class, SearchHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
  /** 提供页面缓存数据访问对象。 */
  abstract fun pageCacheDao(): PageCacheDao

  /** 提供历史数据访问对象。 */
  abstract fun historyDao(): HistoryDao
}

/** KMP Room 数据库构造器（actual 由 Room KSP 自动生成）。 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>

/** 平台数据库构造器工厂。 */
fun interface AppDatabaseBuilderFactory {
  /** 创建数据库构造器。 */
  fun create(): RoomDatabase.Builder<AppDatabase>
}
