package me.domino.fa2.data.taxonomy

import fa2.shared.generated.resources.Res
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val taxonomyResourcePath = "files/fa-tags.json"

private val taxonomyJson: Json = Json {
  ignoreUnknownKeys = true
  isLenient = true
  explicitNulls = false
}

internal suspend fun loadFaTaxonomyCatalog(): FaTaxonomyCatalog =
    taxonomyJson.decodeFromString(Res.readBytes(taxonomyResourcePath).decodeToString())
