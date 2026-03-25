package me.domino.fa2.data.taxonomy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FaTaxonomyRepositoryTest {
  @Test
  fun loads_expected_examples_and_order() = runTest {
    val repository = FaTaxonomyRepository()

    repository.ensureLoaded()

    assertEquals(32, repository.findCategoryIdByEnglishLabel("Food / Recipes"))
    assertTrue(!repository.categoryDisplayNameById(32).isNullOrBlank())
    assertEquals(13, repository.findTypeIdByEnglishLabel("Tutorials"))
    assertEquals(3, repository.findTypeIdByEnglishLabel("Animal related (non-anthro)"))
    assertEquals(5001, repository.findSpeciesIdByEnglishLabel("Alien (Other)"))

    val speciesGroups = repository.speciesOptionGroups()
    assertTrue(speciesGroups.isNotEmpty())
    assertEquals("", speciesGroups.first().label)
    assertEquals(1, speciesGroups.first().options.single().value)
    assertTrue(speciesGroups.first().options.single().label.isNotBlank())

    val speciesGroup = assertNotNull(repository.speciesGroupById(1))
    assertEquals("sg_ungrouped_0", speciesGroup.key)

    val categoryGroups = repository.categoryOptionGroups()
    assertTrue(categoryGroups.size >= 2)
    assertEquals(1, categoryGroups[0].options.first().value)
    assertEquals(37, categoryGroups[1].options.first().value)
    assertEquals("image", repository.categoryCardIconByTag("c_wallpaper"))
    assertEquals("other", repository.categoryCardIconByTag("c_other"))
  }
}
