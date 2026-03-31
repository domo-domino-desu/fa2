package me.domino.fa2.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** 轻量 KV 存储封装。 */
class KeyValueStorage(
    /** DataStore Preferences 实例。 */
    private val dataStore: DataStore<Preferences>
) {
  /**
   * 读取字符串值。
   *
   * @param key KV 键。
   */
  suspend fun load(key: String): String? {
    val preferenceKey = stringPreferencesKey(key)
    return dataStore.data.first()[preferenceKey]
  }

  /**
   * 保存字符串值。
   *
   * @param key KV 键。
   * @param value KV 值。
   */
  suspend fun save(key: String, value: String) {
    val preferenceKey = stringPreferencesKey(key)
    dataStore.edit { preferences -> preferences[preferenceKey] = value }
  }

  /**
   * 删除指定键。
   *
   * @param key KV 键。
   */
  suspend fun delete(key: String) {
    val preferenceKey = stringPreferencesKey(key)
    dataStore.edit { preferences -> preferences.remove(preferenceKey) }
  }

  companion object {
    /** HTTP UA 的 KV 键。 */
    const val KEY_HTTP_USER_AGENT: String = "http_user_agent"

    /** 当前登录用户名的 KV 键。 */
    const val KEY_AUTH_PERSISTED_USERNAME: String = "auth.persisted_username"

    /** 是否需要重新登录的 KV 键。 */
    const val KEY_AUTH_NEEDS_RELOGIN: String = "auth.needs_relogin"
  }
}
