package me.domino.fa2.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiUtilsTest {
  @Test
  fun recognizesGifByPathExtensionOnly() {
    assertTrue(isGifUrl("https://d.furaffinity.net/art/demo/image.gif"))
    assertTrue(isGifUrl("https://d.furaffinity.net/art/demo/image.gif?token=abc#frag"))
  }

  @Test
  fun rejectsGifLikeQueryNoiseAndDifferentExtensions() {
    assertFalse(isGifUrl("https://d.furaffinity.net/art/demo/image.png?format=.gif"))
    assertFalse(isGifUrl("https://d.furaffinity.net/art/demo/image.gifv"))
    assertFalse(isGifUrl("https://d.furaffinity.net/art/demo/no-extension"))
  }
}
