package me.domino.fa2.ui.components.paging

import me.domino.fa2.data.model.QueryError
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.errorOrNull

enum class PageWindowLoadKind {
  PREPEND,
  APPEND,
  JUMP,
}

data class PageWindowLoading<Error>(
    val initial: Boolean = false,
    val prepend: Boolean = false,
    val append: Boolean = false,
    val jump: Boolean = false,
    val prependError: Error? = null,
    val appendError: Error? = null,
    val jumpError: Error? = null,
) {
  val any: Boolean
    get() = initial || prepend || append || jump

  fun begin(kind: PageWindowLoadKind): PageWindowLoading<Error> =
      when (kind) {
        PageWindowLoadKind.PREPEND -> copy(prepend = true, prependError = null)
        PageWindowLoadKind.APPEND -> copy(append = true, appendError = null)
        PageWindowLoadKind.JUMP -> copy(jump = true, jumpError = null)
      }

  fun fail(kind: PageWindowLoadKind, error: Error): PageWindowLoading<Error> =
      when (kind) {
        PageWindowLoadKind.PREPEND -> copy(prepend = false, prependError = error)
        PageWindowLoadKind.APPEND -> copy(append = false, appendError = error)
        PageWindowLoadKind.JUMP -> copy(jump = false, jumpError = error)
      }

  fun normalizedForRestore(): PageWindowLoading<Error> =
      copy(initial = false, prepend = false, append = false, jump = false)
}

data class PageWindowState<Page, Item, Error>(
    val pages: List<Page> = emptyList(),
    val items: List<Item> = emptyList(),
    val selectedItemKey: Any? = null,
    val selectedIndex: Int = 0,
    val loading: PageWindowLoading<Error> = PageWindowLoading(),
) {
  fun normalizedForRestore(): PageWindowState<Page, Item, Error> =
      copy(loading = loading.normalizedForRestore())
}

class PageWindowReducer<Page, Item, PageKey>(
    private val pageKeyOf: (Page) -> PageKey,
    private val itemsOf: (Page) -> List<Item>,
    private val itemKeyOf: (Item) -> Any,
) {
  fun <Error> beginPrepend(
      state: PageWindowState<Page, Item, Error>
  ): PageWindowState<Page, Item, Error> =
      state.copy(loading = state.loading.begin(PageWindowLoadKind.PREPEND))

  fun <Error> beginAppend(
      state: PageWindowState<Page, Item, Error>
  ): PageWindowState<Page, Item, Error> =
      state.copy(loading = state.loading.begin(PageWindowLoadKind.APPEND))

  fun <Error> beginJump(
      state: PageWindowState<Page, Item, Error>
  ): PageWindowState<Page, Item, Error> =
      state.copy(loading = state.loading.begin(PageWindowLoadKind.JUMP))

  fun <Error> reducePrepend(
      state: PageWindowState<Page, Item, Error>,
      page: Page,
  ): PageWindowState<Page, Item, Error> =
      rebuild(state, mergePages(state.pages, page, prepend = true), PageWindowLoading())

  fun <Error> reduceAppend(
      state: PageWindowState<Page, Item, Error>,
      page: Page,
  ): PageWindowState<Page, Item, Error> =
      rebuild(state, mergePages(state.pages, page, prepend = false), PageWindowLoading())

  fun <Error> reduceJump(
      state: PageWindowState<Page, Item, Error>,
      page: Page,
      replace: Boolean,
  ): PageWindowState<Page, Item, Error> =
      rebuild(
          state,
          if (replace) listOf(page) else mergePages(state.pages, page, false),
          PageWindowLoading(),
      )

  fun <Error> reduceError(
      state: PageWindowState<Page, Item, Error>,
      kind: PageWindowLoadKind,
      error: Error,
  ): PageWindowState<Page, Item, Error> = state.copy(loading = state.loading.fail(kind, error))

  fun mergeItems(existing: List<Item>, incoming: List<Item>): List<Item> {
    if (incoming.isEmpty()) return existing
    val merged = LinkedHashMap<Any, Item>(existing.size + incoming.size)
    existing.forEach { item -> merged[itemKeyOf(item)] = item }
    incoming.forEach { item -> merged[itemKeyOf(item)] = item }
    return merged.values.toList()
  }

  private fun mergePages(existing: List<Page>, incoming: Page, prepend: Boolean): List<Page> {
    if (existing.any { pageKeyOf(it) == pageKeyOf(incoming) }) return existing
    return if (prepend) listOf(incoming) + existing else existing + incoming
  }

  private fun <Error> rebuild(
      state: PageWindowState<Page, Item, Error>,
      pages: List<Page>,
      loading: PageWindowLoading<Error>,
  ): PageWindowState<Page, Item, Error> {
    val items = pages.flatMap(itemsOf).distinctBy(itemKeyOf)
    val selectedKey = state.selectedItemKey?.takeIf { key -> items.any { itemKeyOf(it) == key } }
    val selectedIndex =
        selectedKey?.let { key -> items.indexOfFirst { itemKeyOf(it) == key } }?.coerceAtLeast(0)
            ?: 0
    return state.copy(
        pages = pages,
        items = items,
        selectedItemKey = selectedKey,
        selectedIndex = selectedIndex,
        loading = loading,
    )
  }
}

data class AppendPageResult<Item>(
    val items: List<Item>,
    val nextPageUrl: String?,
)

fun <Item, Page> ResourceResult<Page>.reduceAppendResult(
    existingItems: List<Item>,
    itemsOf: (Page) -> List<Item>,
    nextPageUrlOf: (Page) -> String?,
    keyOf: (Item) -> Any,
): ResourceResult<AppendPageResult<Item>> =
    when (this) {
      is ResourceResult.Success -> {
        val reducer =
            PageWindowReducer<Page, Item, String>(
                pageKeyOf = { "append" },
                itemsOf = itemsOf,
                itemKeyOf = keyOf,
            )
        ResourceResult.Success(
            AppendPageResult(
                items = reducer.mergeItems(existingItems, itemsOf(data)),
                nextPageUrl = nextPageUrlOf(data),
            )
        )
      }
      else -> ResourceResult.Error(errorOrNull()!!.asThrowable())
    }

private fun QueryError.asThrowable(): Throwable =
    when (this) {
      is QueryError.AuthRequired -> IllegalStateException(message)
      QueryError.CfChallenge -> IllegalStateException("Cloudflare challenge")
      is QueryError.MatureBlocked -> IllegalStateException(reason)
      is QueryError.Exception -> cause
    }
