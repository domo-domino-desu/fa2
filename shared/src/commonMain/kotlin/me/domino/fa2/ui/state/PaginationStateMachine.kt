package me.domino.fa2.ui.state

import me.domino.fa2.data.model.PageState

data class PaginationSnapshot<Item>(
    val items: List<Item>,
    val nextPageUrl: String?,
    val loading: Boolean,
    val refreshing: Boolean,
    val isLoadingMore: Boolean,
    val errorMessage: String?,
    val appendErrorMessage: String?,
)

class PaginationStateMachine<Item, Key>(
    private val keyOf: (Item) -> Key,
    private val challengeMessage: String = "需要 Cloudflare 验证",
    private val appendFallbackErrorMessage: String = "加载失败，请重试。",
) {
  fun beginLoad(
      snapshot: PaginationSnapshot<Item>,
      forceRefresh: Boolean,
      clearExisting: Boolean = false,
  ): PaginationSnapshot<Item> {
    val baseItems = if (clearExisting) emptyList() else snapshot.items
    val hasExisting = baseItems.isNotEmpty()
    return snapshot.copy(
        items = baseItems,
        nextPageUrl = if (clearExisting) null else snapshot.nextPageUrl,
        loading = !hasExisting,
        refreshing = hasExisting || forceRefresh,
        isLoadingMore = false,
        errorMessage = null,
        appendErrorMessage = null,
    )
  }

  fun canLoadMore(snapshot: PaginationSnapshot<Item>, force: Boolean): Boolean {
    if (snapshot.nextPageUrl.isNullOrBlank()) return false
    if (snapshot.isLoadingMore || snapshot.loading || snapshot.refreshing) return false
    if (!force && !snapshot.appendErrorMessage.isNullOrBlank()) return false
    return true
  }

  fun beginAppend(snapshot: PaginationSnapshot<Item>): PaginationSnapshot<Item> =
      snapshot.copy(isLoadingMore = true, appendErrorMessage = null)

  fun <Page> reduceFirstPage(
      snapshot: PaginationSnapshot<Item>,
      result: PageState<Page>,
      itemsOf: (Page) -> List<Item>,
      nextPageUrlOf: (Page) -> String?,
  ): PaginationSnapshot<Item> =
      when (result) {
        is PageState.Success ->
            snapshot.copy(
                items = itemsOf(result.data),
                nextPageUrl = nextPageUrlOf(result.data),
                loading = false,
                refreshing = false,
                isLoadingMore = false,
                errorMessage = null,
                appendErrorMessage = null,
            )

        PageState.CfChallenge ->
            snapshot.copy(
                loading = false,
                refreshing = false,
                isLoadingMore = false,
                errorMessage = challengeMessage,
                appendErrorMessage = null,
            )

        is PageState.MatureBlocked ->
            snapshot.copy(
                loading = false,
                refreshing = false,
                isLoadingMore = false,
                errorMessage = result.reason,
            )

        is PageState.Error ->
            snapshot.copy(
                loading = false,
                refreshing = false,
                isLoadingMore = false,
                errorMessage = result.exception.readableMessage(),
            )

        PageState.Loading -> snapshot
      }

  fun <Page> reduceAppend(
      snapshot: PaginationSnapshot<Item>,
      result: PageState<Page>,
      itemsOf: (Page) -> List<Item>,
      nextPageUrlOf: (Page) -> String?,
  ): PaginationSnapshot<Item> =
      when (result) {
        is PageState.Success ->
            snapshot.copy(
                items = mergeByKey(existing = snapshot.items, incoming = itemsOf(result.data)),
                nextPageUrl = nextPageUrlOf(result.data),
                isLoadingMore = false,
                appendErrorMessage = null,
            )

        PageState.CfChallenge ->
            snapshot.copy(isLoadingMore = false, appendErrorMessage = challengeMessage)

        is PageState.MatureBlocked ->
            snapshot.copy(isLoadingMore = false, appendErrorMessage = result.reason)

        is PageState.Error ->
            snapshot.copy(
                isLoadingMore = false,
                appendErrorMessage = result.exception.readableMessage(),
            )

        PageState.Loading ->
            snapshot.copy(
                isLoadingMore = false,
                appendErrorMessage = appendFallbackErrorMessage,
            )
      }

  private fun mergeByKey(existing: List<Item>, incoming: List<Item>): List<Item> {
    if (incoming.isEmpty()) return existing
    val map = LinkedHashMap<Key, Item>(existing.size + incoming.size)
    existing.forEach { item -> map[keyOf(item)] = item }
    incoming.forEach { item -> map[keyOf(item)] = item }
    return map.values.toList()
  }
}

private fun Throwable.readableMessage(): String = message ?: toString()
