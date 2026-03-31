package me.domino.fa2.di

/** Cookie 加密存储实例的 Koin qualifier。 */
const val KOIN_QUALIFIER_COOKIE_VAULT: String = "cookie_vault"

/** 设置机密项加密存储实例的 Koin qualifier。 */
const val KOIN_QUALIFIER_SETTINGS_SECRET_VAULT: String = "settings_secret_vault"

/** 原始 HTML 数据源 qualifier。 */
const val KOIN_QUALIFIER_RAW_HTML_DATA_SOURCE: String = "rawHtmlDataSource"

/** 仅带 Cloudflare 感知的 HTML 数据源 qualifier。 */
const val KOIN_QUALIFIER_CHALLENGE_AWARE_HTML_DATA_SOURCE: String = "challengeAwareHtmlDataSource"

/** 社交动作专用 HTTP 客户端 qualifier。 */
const val KOIN_QUALIFIER_SOCIAL_ACTION_CLIENT: String = "socialActionClient"
