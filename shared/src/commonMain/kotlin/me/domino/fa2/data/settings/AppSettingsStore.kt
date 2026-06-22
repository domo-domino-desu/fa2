package me.domino.fa2.data.settings

interface AppSettingsStore {
  suspend fun load(): AppSettings

  suspend fun save(settings: AppSettings)
}
