package me.domino.fa2.data.fa.core

import kotlinx.serialization.json.Json

internal val storeJson: Json = Json {
  ignoreUnknownKeys = true
  isLenient = true
  explicitNulls = false
}
