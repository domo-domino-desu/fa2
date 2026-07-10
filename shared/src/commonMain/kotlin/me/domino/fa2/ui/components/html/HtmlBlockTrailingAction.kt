package me.domino.fa2.ui.components.html

internal data class HtmlBlockTrailingAction(
    val state: HtmlBlockTrailingActionState,
    val contentDescription: String,
    val anchorTextLineEndOffset: Int?,
    val onClick: () -> Unit,
)

internal enum class HtmlBlockTrailingActionState {
  Idle,
  Loading,
  Resolved,
}
