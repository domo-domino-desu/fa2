package me.domino.fa2.di

import io.ktor.client.HttpClient
import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.util.logging.FaLog
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module

private val log = FaLog.withTag("AppModule")

/** 汇总应用模块。 */
fun appModules(platformModule: Module): List<Module> =
    listOf(
        networkModule(),
        databaseModule(),
        dataSourceModule(),
        storeModule(),
        repositoryModule(),
        screenModelModule(),
        platformModule,
    )

/** 初始化应用 Koin。 */
fun startAppKoin(platformModule: Module): Koin {
  val existing = GlobalContext.getOrNull()
  if (existing != null) return existing
  return startKoin { modules(appModules(platformModule)) }.koin
}

/** 关闭应用 Koin 并释放关键资源。 */
fun stopAppKoin() {
  val koin = GlobalContext.getOrNull() ?: return
  runCatching { koin.get<HttpClient>().close() }
      .onFailure { error -> log.e(error) { "关闭 Koin -> HttpClient 关闭失败" } }
  runCatching { koin.get<AppDatabase>().close() }
      .onFailure { error -> log.e(error) { "关闭 Koin -> AppDatabase 关闭失败" } }
  stopKoin()
}
