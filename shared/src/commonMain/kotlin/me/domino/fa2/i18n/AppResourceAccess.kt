package me.domino.fa2.i18n

import fa2.shared.generated.resources.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

fun appString(resource: StringResource, vararg formatArgs: Any): String = runBlocking {
  getString(resource, *formatArgs)
}

fun challengeAwaitingUserActionText(cfRay: String?): String {
  val rayText = cfRay?.trim()?.takeIf { it.isNotBlank() }?.let { "\nCF-Ray: $it" }.orEmpty()
  return appString(Res.string.challenge_awaiting_user_action, rayText)
}

fun challengeVerificationFailedText(detail: String?): String =
    appString(Res.string.challenge_verification_failed, detail?.trim().orEmpty())

fun registeredAtText(value: String): String =
    value
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { appString(Res.string.registered_at, it) }
        .orEmpty()

fun mustBeInRangeText(label: String, min: Int, max: Int, suffix: String = ""): String {
  val normalizedSuffix = suffix.trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
  return appString(Res.string.must_be_in_range, label, min, max, normalizedSuffix)
}
