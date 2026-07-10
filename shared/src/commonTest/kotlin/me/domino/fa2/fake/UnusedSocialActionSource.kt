package me.domino.fa2.fake

import me.domino.fa2.data.remote.fa.social.SocialActionResult
import me.domino.fa2.data.remote.fa.social.SocialActionSource

/** Explicit test seam for repository tests that never exercise social mutations. */
object UnusedSocialActionSource : SocialActionSource {
  override suspend fun execute(actionUrl: String): SocialActionResult =
      error("Social action is outside this test's scope")

  override suspend fun updateTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): SocialActionResult = error("Tag blocking is outside this test's scope")
}
