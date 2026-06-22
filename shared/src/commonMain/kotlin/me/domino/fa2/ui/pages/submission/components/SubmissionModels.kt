package me.domino.fa2.ui.pages.submission.components

import androidx.compose.ui.graphics.vector.ImageVector

internal data class SubmissionBrowseFilter(val category: Int?, val type: Int?, val species: Int?)

internal data class SubmissionInfoMetric(
    val icon: ImageVector,
    val text: String,
    val onClick: (() -> Unit)? = null,
)
