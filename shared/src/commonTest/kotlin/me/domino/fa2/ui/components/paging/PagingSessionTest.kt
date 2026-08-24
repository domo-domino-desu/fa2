package me.domino.fa2.ui.components.paging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PagingSessionTest {
  @Test
  fun jumpUpdatesPageImmediatelyAndScrollsToTop() = runTest {
    val session =
        DefaultPagingSession(
            this,
            PagingSource { page, _ -> PagingPage(page, listOf("p$page"), 5) },
            { it },
        )
    val effect = async(start = CoroutineStart.UNDISPATCHED) { session.effects.first() }
    session.accept(PagingIntent.JumpToPage(3))
    assertEquals(3, session.state.value.currentPage)
    runCurrent()
    assertIs<PagingEffect.ScrollToTop>(effect.await())
    advanceUntilIdle()
    assertEquals(listOf("p3"), session.state.value.items)
  }

  @Test
  fun failedJumpRollsBack() = runTest {
    val session =
        DefaultPagingSession(
            this,
            PagingSource<String> { _, _ -> error("failed") },
            { it },
            PagingPage(2, listOf("old"), 5),
        )
    session.accept(PagingIntent.ViewportChanged(PagingAnchor("old", 4, 12)))
    val restore =
        async(start = CoroutineStart.UNDISPATCHED) {
          session.effects.first { it is PagingEffect.RestoreViewport }
        }
    session.accept(PagingIntent.JumpToPage(4))
    advanceUntilIdle()
    assertEquals(2, session.state.value.currentPage)
    assertEquals(listOf("old"), session.state.value.items)
    assertEquals(
        PagingAnchor("old", 4, 12),
        assertIs<PagingEffect.RestoreViewport>(restore.await()).anchor,
    )
  }
}
