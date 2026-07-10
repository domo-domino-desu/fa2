package me.domino.fa2.ui.components.state

import kotlin.test.Test
import kotlin.test.assertEquals

class PageStateWrapperPolicyTest {
  @Test
  fun `generic error falls back when there is no content`() {
    assertEquals(
        PageFailureDisplayMode.HardFallback,
        resolvePageFailureDisplayMode(
            state = UiPageState.Error(IllegalStateException("boom")),
            hasContent = false,
        ),
    )
  }

  @Test
  fun `generic error keeps content visible when content exists`() {
    assertEquals(
        PageFailureDisplayMode.PassThrough,
        resolvePageFailureDisplayMode(
            state = UiPageState.Error(IllegalStateException("boom")),
            hasContent = true,
        ),
    )
  }

  @Test
  fun `blocking page states always use hard fallback`() {
    assertEquals(
        PageFailureDisplayMode.HardFallback,
        resolvePageFailureDisplayMode(
            state = UiPageState.AuthRequired(requestUrl = "https://example.com", message = "auth"),
            hasContent = true,
        ),
    )
    assertEquals(
        PageFailureDisplayMode.HardFallback,
        resolvePageFailureDisplayMode(state = UiPageState.CfChallenge, hasContent = true),
    )
    assertEquals(
        PageFailureDisplayMode.HardFallback,
        resolvePageFailureDisplayMode(
            state = UiPageState.MatureBlocked(reason = "mature"),
            hasContent = true,
        ),
    )
  }
}
