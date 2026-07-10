package me.domino.fa2.ui.i18n

import me.domino.fa2.utils.concurrency.runSuspendCatching
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

suspend fun appString(resource: StringResource, vararg formatArgs: Any): String =
    getString(resource, *formatArgs)

suspend fun appStringOrFallback(
    resource: StringResource,
    fallback: () -> String,
    vararg formatArgs: Any,
): String = runSuspendCatching { appString(resource, *formatArgs) }.getOrElse { fallback() }
