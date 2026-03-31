package me.domino.fa2.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaginationRetryBarModelTest {
  @Test
  fun `prepend bar hides when there is nothing to show`() {
    val model =
        resolvePaginationRetryBarModel(
            direction = PaginationRetryDirection.Prepend,
            canLoad = false,
            loading = false,
            hasError = false,
        )

    assertFalse(model.visible)
  }

  @Test
  fun `append bar shows manual retry after load more failure`() {
    val model =
        resolvePaginationRetryBarModel(
            direction = PaginationRetryDirection.Append,
            canLoad = true,
            loading = false,
            hasError = true,
        )

    assertTrue(model.visible)
    assertEquals(PaginationRetryTextKey.AppendError, model.textKey)
    assertEquals(PaginationRetryActionKey.ManualLoadNext, model.actionKey)
  }

  @Test
  fun `append bar shows end state when there is no more content`() {
    val model =
        resolvePaginationRetryBarModel(
            direction = PaginationRetryDirection.Append,
            canLoad = false,
            loading = false,
            hasError = false,
        )

    assertTrue(model.visible)
    assertEquals(PaginationRetryTextKey.End, model.textKey)
    assertEquals(null, model.actionKey)
  }
}
