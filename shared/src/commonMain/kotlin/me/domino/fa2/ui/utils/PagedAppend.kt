package me.domino.fa2.ui.utils

import me.domino.fa2.data.model.QueryError
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.errorOrNull

/** Outcome of merging a freshly fetched page into an already-loaded list. */
sealed interface AppendOutcome<out Item> {
  /** New page merged in; [items] is deduped and ready to render. */
  data class Appended<Item>(val items: List<Item>, val nextPageUrl: String?) : AppendOutcome<Item>

  /** Fetch failed; existing items/cursor are untouched by the caller. */
  data class Failed(val error: QueryError) : AppendOutcome<Nothing>
}

/**
 * Merges a next-page [ResourceResult] into [existingItems], deduping by [keyOf].
 *
 * Every list screen model's `loadMore()` used to hand-roll this `when(result){ Success ->
 * dedupe+append; else -> keep items, surface error }` branch. This is the one shared shape;
 * page-specific state (loading flags, folder/shuffle state) stays with the caller.
 */
fun <Item, Page> ResourceResult<Page>.appendTo(
    existingItems: List<Item>,
    itemsOf: (Page) -> List<Item>,
    nextPageUrlOf: (Page) -> String?,
    keyOf: (Item) -> Any,
): AppendOutcome<Item> =
    when (this) {
      is ResourceResult.Success ->
          AppendOutcome.Appended(
              items = (existingItems + itemsOf(data)).distinctBy(keyOf),
              nextPageUrl = nextPageUrlOf(data),
          )
      else -> AppendOutcome.Failed(errorOrNull()!!)
    }
