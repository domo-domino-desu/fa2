package me.domino.fa2.ui.pages.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchFormStateValidationTest {
  @Test
  fun `blank query cannot submit`() {
    assertFalse(isSearchFormSubmittable(SearchFormState(query = "   ")))
  }

  @Test
  fun `manual range without both dates cannot submit`() {
    assertFalse(
        isSearchFormSubmittable(
            SearchFormState(
                query = "dragon",
                range = "manual",
                rangeFrom = "2026-04-01",
                rangeTo = "",
            )
        )
    )
  }

  @Test
  fun `manual range with complete dates can submit`() {
    assertTrue(
        isSearchFormSubmittable(
            SearchFormState(
                query = "dragon",
                range = "manual",
                rangeFrom = "2026-04-01",
                rangeTo = "2026-04-08",
            )
        )
    )
  }

  @Test
  fun `non manual range only requires query`() {
    assertTrue(isSearchFormSubmittable(SearchFormState(query = "dragon", range = "1year")))
  }
}
