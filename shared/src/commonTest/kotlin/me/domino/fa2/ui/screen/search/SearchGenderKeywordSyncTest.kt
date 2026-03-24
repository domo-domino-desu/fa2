package me.domino.fa2.ui.screen.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Search gender 与 query 同步测试。 */
class SearchGenderKeywordSyncTest {
  @Test
  fun parsesGendersFromKeywordsSegment() {
    val genders = parseGendersFromQuery("wolf @keywords female trans_female")
    assertTrue(SearchGender.Female in genders)
    assertTrue(SearchGender.TransFemale in genders)
    assertEquals(2, genders.size)
  }

  @Test
  fun rewritesKeywordsWhenGenderSelectionChanges() {
    val rewritten =
      rewriteQueryWithGenders(
        query = "wolf @keywords female cute",
        genders = setOf(SearchGender.TransFemale),
      )
    assertEquals("wolf @keywords cute trans_female", rewritten)
  }
}
