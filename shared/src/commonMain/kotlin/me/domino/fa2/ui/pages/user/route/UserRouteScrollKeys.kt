package me.domino.fa2.ui.pages.user.route

internal fun buildUserJournalsScrollKey(username: String): String =
    "user-scroll:${username.lowercase()}:journals"

internal fun buildUserSubmissionScrollKey(
    username: String,
    route: UserChildRoute,
    folderUrl: String?,
): String {
  val normalizedFolderUrl = folderUrl?.trim()?.takeIf { it.isNotBlank() } ?: "root"
  return "user-scroll:${username.lowercase()}:${route.routeKey}:$normalizedFolderUrl"
}
