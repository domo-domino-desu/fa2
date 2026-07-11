package me.domino.fa2.ui.components.state

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.domino.fa2.data.model.QueryError
import me.domino.fa2.data.model.QueryState

class PageStateWrapperPolicyTest {
  @Test
  fun `failure without data uses fallback`() {
    assertFalse(shouldShowQueryContent(QueryState.Failure<String>(QueryError.CfChallenge)))
  }

  @Test
  fun `failure with stale data keeps content visible`() {
    assertTrue(
        shouldShowQueryContent(QueryState.Failure(QueryError.Exception(Exception("boom")), "old"))
    )
  }

  @Test
  fun `loading and success render content`() {
    assertTrue(shouldShowQueryContent(QueryState.Loading<String>()))
    assertTrue(shouldShowQueryContent(QueryState.Success("ready")))
  }
}
