package me.domino.fa2.data.fa.media

fun interface ImageBytesSource {
  suspend fun fetch(imageUrl: String): ByteArray
}
