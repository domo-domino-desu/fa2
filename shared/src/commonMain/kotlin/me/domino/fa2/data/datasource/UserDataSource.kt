package me.domino.fa2.data.datasource

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import me.domino.fa2.data.network.endpoint.UserEndpoint
import me.domino.fa2.data.parser.UserParser
import me.domino.fa2.util.toPageState

/** User 主页远端数据源。 */
class UserDataSource(private val endpoint: UserEndpoint, private val parser: UserParser) {
  /** 拉取 user header。 */
  suspend fun fetchUser(username: String): PageState<User> =
      endpoint.fetch(username).toPageState { success ->
        parser.parse(html = success.body, url = success.url)
      }
}
