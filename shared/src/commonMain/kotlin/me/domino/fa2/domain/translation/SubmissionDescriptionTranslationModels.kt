package me.domino.fa2.domain.translation

/** 描述块。 */
data class SubmissionDescriptionBlock(val originalHtml: String, val sourceText: String)

sealed interface SubmissionDescriptionBlockResult {
  data class Success(val translatedText: String) : SubmissionDescriptionBlockResult

  data object EmptyResult : SubmissionDescriptionBlockResult

  data class Failure(val cause: Throwable) : SubmissionDescriptionBlockResult
}
