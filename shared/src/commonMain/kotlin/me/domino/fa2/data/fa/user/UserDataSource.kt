package me.domino.fa2.data.fa.user

import me.domino.fa2.data.fa.core.toPageState
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User

/** User 主页远端数据源。 */
class UserDataSource(private val endpoint: UserEndpoint, private val parser: UserParser) {
  /** 拉取 user header。 */
  suspend fun fetchUser(username: String): PageState<User> =
      endpoint.fetch(username).toPageState { success ->
        parser.parse(html = success.body, url = success.url)
      }
}
