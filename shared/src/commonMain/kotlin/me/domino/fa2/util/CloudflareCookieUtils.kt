package me.domino.fa2.util

/** 判断 cookie 名是否属于 Cloudflare。 */
fun isCloudflareCookieName(cookieName: String): Boolean {
  val lowered = cookieName.trim().lowercase()
  return lowered == "cf_clearance" || lowered.startsWith("cf_") || lowered.startsWith("__cf")
}
