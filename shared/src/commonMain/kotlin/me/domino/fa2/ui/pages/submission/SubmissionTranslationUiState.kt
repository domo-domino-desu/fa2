package me.domino.fa2.ui.pages.submission

import me.domino.fa2.data.translation.SubmissionDescriptionBlock
import me.domino.fa2.ui.state.SubmissionDescriptionDisplayBlock

data class SubmissionTranslationUiState(
    val sourceKey: String,
    val sourceHtml: String,
    val sourceBlocks: List<SubmissionDescriptionBlock>,
    val blocks: List<SubmissionDescriptionDisplayBlock>,
    val translating: Boolean = false,
    val hasTriggered: Boolean = false,
    val sourceFileName: String? = null,
)
