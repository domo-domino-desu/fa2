package me.domino.fa2.ui.pages.submission.content

import androidx.compose.ui.graphics.vector.ImageVector
import me.domino.fa2.ui.pages.submission.attachmenttext.*
import me.domino.fa2.ui.pages.submission.imageocr.*
import me.domino.fa2.ui.pages.submission.pager.*
import me.domino.fa2.ui.pages.submission.series.*
import me.domino.fa2.ui.pages.submission.translation.*

internal data class SubmissionBrowseFilter(val category: Int?, val type: Int?, val species: Int?)

internal data class SubmissionInfoMetric(
    val icon: ImageVector,
    val text: String,
    val onClick: (() -> Unit)? = null,
)
