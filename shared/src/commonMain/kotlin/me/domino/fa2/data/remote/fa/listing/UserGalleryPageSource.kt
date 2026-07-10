package me.domino.fa2.data.remote.fa.listing

import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.ResourceResult

/** Source contract shared by gallery-shaped FA resources. */
interface UserGalleryPageSource {
  suspend fun fetchPage(username: String, nextPageUrl: String?): ResourceResult<GalleryPage>
}

/** Narrow invalidation port used after a favorite mutation. */
interface FavoritesCacheInvalidator {
  suspend fun invalidateFavorites()
}
