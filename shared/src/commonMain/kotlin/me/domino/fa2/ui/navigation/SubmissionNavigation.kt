package me.domino.fa2.ui.navigation

import cafe.adriel.voyager.navigator.Navigator
import me.domino.fa2.ui.pages.submission.SubmissionRouteScreen

internal fun Navigator.openSubmissionFromList(
    sid: Int,
    holderTag: String = "submission-list-holder",
    onSelect: (Int) -> Unit,
) {
  onSelect(sid)
  push(SubmissionRouteScreen(initialSid = sid, holderTag = holderTag))
}
