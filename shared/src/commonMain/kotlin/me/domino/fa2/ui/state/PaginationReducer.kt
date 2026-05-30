package me.domino.fa2.ui.state

import me.domino.fa2.data.model.PageState

/** 分页列表的 UI 状态快照。 */
data class PaginationSnapshot<Item>(
    /** 当前已加载的条目列表。 */
    val items: List<Item>,
    /** 下一页的请求 URL，为 null 表示无更多页。 */
    val nextPageUrl: String?,
    /** 是否正在进行首屏加载。 */
    val loading: Boolean,
    /** 是否正在刷新。 */
    val refreshing: Boolean,
    /** 是否正在追加加载更多。 */
    val isLoadingMore: Boolean,
    /** 首屏/刷新错误信息。 */
    val errorMessage: String?,
    /** 追加加载错误信息。 */
    val appendErrorMessage: String?,
)

/** 分页列表的状态归约器，封装首屏加载、追加加载及错误处理逻辑。 */
class PaginationReducer<Item, Key>(
    /** 从条目中提取去重键的函数。 */
    private val keyOf: (Item) -> Key,
    /** 获取 CF Challenge 错误提示文本的函数。 */
    private val challengeMessage: () -> String,
    /** 获取追加加载兜底错误提示文本的函数。 */
    private val appendFallbackErrorMessage: () -> String,
) {
  /** 开始首屏/刷新加载，更新快照为加载中状态。 */
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

  /** 判断当前快照是否满足追加加载更多的条件。 */
  fun canLoadMore(snapshot: PaginationSnapshot<Item>, force: Boolean): Boolean {
    if (snapshot.nextPageUrl.isNullOrBlank()) return false
    if (snapshot.isLoadingMore || snapshot.loading || snapshot.refreshing) return false
    if (!force && !snapshot.appendErrorMessage.isNullOrBlank()) return false
    return true
  }

  /** 开始追加加载，将快照标记为加载更多状态。 */
  fun beginAppend(snapshot: PaginationSnapshot<Item>): PaginationSnapshot<Item> =
      snapshot.copy(isLoadingMore = true, appendErrorMessage = null)

  /** 根据首页加载结果归约快照状态。 */
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

        is PageState.AuthRequired ->
            snapshot.copy(
                loading = false,
                refreshing = false,
                isLoadingMore = false,
                errorMessage = result.message,
                appendErrorMessage = null,
            )

        PageState.CfChallenge ->
            snapshot.copy(
                loading = false,
                refreshing = false,
                isLoadingMore = false,
                errorMessage = challengeMessage(),
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

  /** 根据追加页加载结果归约快照状态，并按键合并去重。 */
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

        is PageState.AuthRequired ->
            snapshot.copy(isLoadingMore = false, appendErrorMessage = result.message)

        PageState.CfChallenge ->
            snapshot.copy(isLoadingMore = false, appendErrorMessage = challengeMessage())

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
                appendErrorMessage = appendFallbackErrorMessage(),
            )
      }

  /** 将现有条目与新增条目按键合并，新条目覆盖同键旧条目。 */
  private fun mergeByKey(existing: List<Item>, incoming: List<Item>): List<Item> {
    if (incoming.isEmpty()) return existing
    val map = LinkedHashMap<Key, Item>(existing.size + incoming.size)
    existing.forEach { item -> map[keyOf(item)] = item }
    incoming.forEach { item -> map[keyOf(item)] = item }
    return map.values.toList()
  }
}

/** 从异常中提取可读的错误信息文本。 */
private fun Throwable.readableMessage(): String = message ?: toString()
