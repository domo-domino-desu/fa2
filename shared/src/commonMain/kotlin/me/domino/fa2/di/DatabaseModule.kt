package me.domino.fa2.di

import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.data.local.AppDatabaseBuilderFactory
import me.domino.fa2.data.local.KeyValueStorage
import org.koin.core.module.Module
import org.koin.dsl.module

/** 数据库相关依赖模块。 */
fun databaseModule(): Module = module {
  single<AppDatabase> { get<AppDatabaseBuilderFactory>().create().build() }
  single { get<AppDatabase>().pageCacheDao() }
  single { get<AppDatabase>().historyDao() }
  single { KeyValueStorage(get()) }
}
