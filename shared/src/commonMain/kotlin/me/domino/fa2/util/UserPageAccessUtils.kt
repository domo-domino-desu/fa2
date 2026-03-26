package me.domino.fa2.util

import com.fleeksoft.ksoup.nodes.Document

/** 若页面是系统消息页则抛出业务可读异常。 */
fun ensureUserPageAccessible(document: Document) {
  val title = document.title().trim().lowercase()
  val bodyText = document.selectFirst("body")?.text()?.lowercase().orEmpty()

  if (title.contains("system error") && bodyText.contains("cannot be found")) {
    throw IllegalStateException("This user cannot be found")
  }
  if (bodyText.contains("access has been disabled to the account and contents of user")) {
    throw IllegalStateException("Access to this user has been disabled")
  }
  if (bodyText.contains("currently pending deletion")) {
    throw IllegalStateException("This user page is pending deletion")
  }
}
