package me.domino.fa2.ui.app.navigation

import cafe.adriel.voyager.core.screen.Screen
import kotlin.random.Random

interface AppScreen : Screen

internal fun nextScreenEntryId(prefix: String): String = "$prefix:${Random.nextLong().toString(16)}"
