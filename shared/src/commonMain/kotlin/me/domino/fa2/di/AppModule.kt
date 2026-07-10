package me.domino.fa2.di

import io.ktor.client.HttpClient
import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.utils.logging.FaLog
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named

private val log = FaLog.withTag("AppModule")

/** 汇总应用模块。 */
fun appModules(platformModule: Module): List<Module> =
    listOf(dataModule(), domainModule(), uiModule(), platformModule)

/** 初始化应用 Koin。 */
fun startAppKoin(platformModule: Module): Koin {
  val existing = GlobalContext.getOrNull()
  if (existing != null) return existing
  return startKoin { modules(appModules(platformModule)) }.koin
}

/** 关闭应用 Koin 并释放关键资源。 */
fun stopAppKoin() {
  val koin = GlobalContext.getOrNull() ?: return
  listOf(
          "default" to runCatching { koin.get<HttpClient>() }.getOrNull(),
          "cached-download" to
              runCatching {
                    koin.get<HttpClient>(qualifier = named(KOIN_QUALIFIER_CACHED_DOWNLOAD_CLIENT))
                  }
                  .getOrNull(),
          "fa-page" to
              runCatching { koin.get<HttpClient>(qualifier = named(KOIN_QUALIFIER_FA_PAGE_CLIENT)) }
                  .getOrNull(),
          "social-action" to
              runCatching {
                    koin.get<HttpClient>(qualifier = named(KOIN_QUALIFIER_SOCIAL_ACTION_CLIENT))
                  }
                  .getOrNull(),
      )
      .distinctBy { (_, client) -> client }
      .forEach { (name, client) ->
        if (client != null) {
          runCatching { client.close() }
              .onFailure { error -> log.e(error) { "关闭 Koin -> $name HttpClient 关闭失败" } }
        }
      }
  runCatching { koin.get<AppDatabase>().close() }
      .onFailure { error -> log.e(error) { "关闭 Koin -> AppDatabase 关闭失败" } }
  stopKoin()
}
