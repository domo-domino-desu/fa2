package me.domino.fa2.data.remote.fa.media

fun interface ImageBytesSource {
  suspend fun fetch(imageUrl: String): ByteArray
}
