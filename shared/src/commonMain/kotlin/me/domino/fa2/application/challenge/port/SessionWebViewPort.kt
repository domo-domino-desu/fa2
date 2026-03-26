package me.domino.fa2.application.challenge.port

/** 通用会话 WebView 交互端口。 */
interface SessionWebViewPort {
  /** 当前最后加载地址。 */
  val lastLoadedUrl: String?

  /**
   * 加载新地址。
   *
   * @param url 目标地址。
   */
  fun loadUrl(url: String)

  /**
   * 抓取指定地址下 WebView cookie header。
   *
   * @param url 目标地址。
   */
  suspend fun captureCookieHeader(url: String): String

  /**
   * 注入 cookie header 到指定地址。
   *
   * @param url 目标地址。
   * @param cookieHeader cookie header。
   */
  suspend fun injectCookieHeader(url: String, cookieHeader: String)

  /** 读取当前 WebView UA。 */
  suspend fun readUserAgent(): String?
}
