package me.domino.fa2.data.remote.fa.core

import kotlinx.serialization.json.Json

internal val cacheJson: Json = Json {
  ignoreUnknownKeys = true
  isLenient = true
  explicitNulls = false
}
