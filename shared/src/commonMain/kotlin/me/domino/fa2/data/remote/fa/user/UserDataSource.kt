package me.domino.fa2.data.remote.fa.user

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.User
import me.domino.fa2.data.remote.fa.core.toResourceResult

/** User 主页远端数据源。 */
class UserDataSource(private val endpoint: UserEndpoint, private val parser: UserParser) {
  /** 拉取 user header。 */
  suspend fun fetchUser(username: String): ResourceResult<User> =
      endpoint.fetch(username).toResourceResult { success ->
        parser.parse(html = success.body, url = success.url)
      }
}
