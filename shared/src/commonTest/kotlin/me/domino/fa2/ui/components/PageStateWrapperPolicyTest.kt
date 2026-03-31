package me.domino.fa2.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import me.domino.fa2.data.model.PageState

class PageStateWrapperPolicyTest {
  @Test
  fun `generic error falls back when there is no content`() {
    assertEquals(
        PageFailureDisplayMode.HardFallback,
        resolvePageFailureDisplayMode(
            state = PageState.Error(IllegalStateException("boom")),
            hasContent = false,
        ),
    )
  }

  @Test
  fun `generic error keeps content visible when content exists`() {
    assertEquals(
        PageFailureDisplayMode.PassThrough,
        resolvePageFailureDisplayMode(
            state = PageState.Error(IllegalStateException("boom")),
            hasContent = true,
        ),
    )
  }

  @Test
  fun `blocking page states always use hard fallback`() {
    assertEquals(
        PageFailureDisplayMode.HardFallback,
        resolvePageFailureDisplayMode(
            state = PageState.AuthRequired(requestUrl = "https://example.com", message = "auth"),
            hasContent = true,
        ),
    )
    assertEquals(
        PageFailureDisplayMode.HardFallback,
        resolvePageFailureDisplayMode(
            state = PageState.CfChallenge,
            hasContent = true,
        ),
    )
    assertEquals(
        PageFailureDisplayMode.HardFallback,
        resolvePageFailureDisplayMode(
            state = PageState.MatureBlocked(reason = "mature"),
            hasContent = true,
        ),
    )
  }
}
