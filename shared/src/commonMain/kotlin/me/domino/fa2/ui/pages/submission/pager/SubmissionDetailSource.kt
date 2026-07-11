package me.domino.fa2.ui.pages.submission.pager

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress

data class SubmissionDetailSource(
    val loadBySid: suspend (Int) -> ResourceResult<Submission>,
    val loadByUrl: suspend (String) -> ResourceResult<Submission>,
    val loadAttachmentText:
        suspend (String, String, (AttachmentTextProgress) -> Unit) -> ResourceResult<
                AttachmentTextDocument
            >,
    val toggleFavorite: suspend (Int, String) -> ResourceResult<Unit>,
    val blockTag: suspend (Int, String, String, Boolean) -> ResourceResult<Unit>,
)
