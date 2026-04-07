package me.domino.fa2.ui.pages.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.domino.fa2.ui.pages.search.util.SearchDateFields
import me.domino.fa2.ui.pages.search.util.SearchDateRangeShiftAction
import me.domino.fa2.ui.pages.search.util.canShiftSearchDateFields
import me.domino.fa2.ui.pages.search.util.isoDateToEpochMillisOrNull
import me.domino.fa2.ui.pages.search.util.normalizeManualDateFields
import me.domino.fa2.ui.pages.search.util.resolveSearchDateFields
import me.domino.fa2.ui.pages.search.util.shiftSearchDateFields

class SearchDateUtilsTest {
  @Test
  fun `preset range resolves against current max date plus one day`() {
    val now = isoDateToEpochMillisOrNull("2026-04-07")!!

    val resolved = resolveSearchDateFields("1year", "", "", now)

    assertEquals(SearchDateFields(from = "2025-04-08", to = "2026-04-08"), resolved)
  }

  @Test
  fun `manual range normalization clamps swaps and preserves valid order`() {
    val now = isoDateToEpochMillisOrNull("2026-04-07")!!

    val normalized = normalizeManualDateFields("2007-01-01", "2005-01-01", now)

    assertEquals(SearchDateFields(from = "2005-12-04", to = "2007-01-01"), normalized)
  }

  @Test
  fun `shifting backward truncates to minimum boundary`() {
    val now = isoDateToEpochMillisOrNull("2026-04-07")!!
    val fields = SearchDateFields(from = "2006-01-01", to = "2007-01-01")

    val shifted = shiftSearchDateFields(fields, SearchDateRangeShiftAction.PreviousYear, now)

    assertEquals(SearchDateFields(from = "2005-12-04", to = "2006-12-04"), shifted)
  }

  @Test
  fun `shifting month preserves month-end semantics`() {
    val now = isoDateToEpochMillisOrNull("2026-04-07")!!
    val fields = SearchDateFields(from = "2024-01-31", to = "2024-02-29")

    val shifted = shiftSearchDateFields(fields, SearchDateRangeShiftAction.NextMonth, now)

    assertEquals(SearchDateFields(from = "2024-02-29", to = "2024-03-29"), shifted)
  }

  @Test
  fun `cannot shift further when range already touches upper boundary`() {
    val now = isoDateToEpochMillisOrNull("2026-04-07")!!
    val fields = SearchDateFields(from = "2026-04-01", to = "2026-04-08")

    assertFalse(canShiftSearchDateFields(fields, SearchDateRangeShiftAction.NextDay, now))
    assertTrue(canShiftSearchDateFields(fields, SearchDateRangeShiftAction.PreviousDay, now))
  }
}
