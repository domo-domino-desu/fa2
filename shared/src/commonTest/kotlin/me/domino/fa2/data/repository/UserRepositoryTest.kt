package me.domino.fa2.data.fa.user

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.data.fa.social.SocialActionEndpoint
import me.domino.fa2.data.model.PageState
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** UserRepository 链路测试。 */
class UserRepositoryTest {
  @Test
  fun loadUserHeaderSuccess() = runTest {
    val source = UserScriptedHtmlDataSource()
    val repository = buildRepository(source)

    source.enqueue(
        url = FaUrls.user("artist-alpha"),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:user:artist-alpha.html"),
                url = FaUrls.user("artist-alpha"),
            ),
    )

    val state = repository.loadUser("artist-alpha")
    assertTrue(state is PageState.Success)
    assertTrue(state.data.username.isNotBlank())
  }

  @Test
  fun loadUserHeaderErrorMessagePage() = runTest {
    val source = UserScriptedHtmlDataSource()
    val repository = buildRepository(source)

    source.enqueue(
        url = FaUrls.user("unknown"),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:user:username-system-error.html"),
                url = FaUrls.user("unknown"),
            ),
    )

    val state = repository.loadUser("unknown")
    assertTrue(state is PageState.Error)
  }

  private fun buildRepository(source: FaHtmlDataSource): UserRepository {
    val store =
        UserPageCache(
            dataSource = UserDataSource(endpoint = UserEndpoint(source), parser = UserParser()),
            pageCacheDao = InMemoryPageCacheDao(),
        )
    return UserRepository(
        userStore = store,
        socialActionEndpoint = SocialActionEndpoint(source),
    )
  }
}

/** 脚本化 HTML 数据源，用于控制请求返回。 */
private class UserScriptedHtmlDataSource : FaHtmlDataSource {
  private val queueByUrl: MutableMap<String, ArrayDeque<HtmlResponseResult>> = mutableMapOf()

  fun enqueue(url: String, response: HtmlResponseResult) {
    queueByUrl.getOrPut(url) { ArrayDeque() }.addLast(response)
  }

  override suspend fun get(url: String): HtmlResponseResult {
    val queue = queueByUrl[url]
    if (queue == null || queue.isEmpty()) {
      return HtmlResponseResult.Error(
          statusCode = 500,
          message = "No scripted response for $url",
      )
    }
    return queue.removeFirst()
  }
}
