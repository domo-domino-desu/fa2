package me.domino.fa2.di

import io.ktor.client.HttpClient
import me.domino.fa2.data.local.AppDatabase
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module

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
fun startAppKoin(platformModule: Module) {
  if (GlobalContext.getOrNull() != null) return
  startKoin { modules(appModules(platformModule)) }
}

/** 关闭应用 Koin 并释放关键资源。 */
fun stopAppKoin() {
  val koin = GlobalContext.getOrNull() ?: return
  runCatching { koin.get<HttpClient>().close() }
  runCatching { koin.get<AppDatabase>().close() }
  stopKoin()
}
