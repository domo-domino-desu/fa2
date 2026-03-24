package me.domino.fa2.ui.pages.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserSectionTopScaffoldTest {
  private val layoutWithHeader = userJournalsScrollLayout(hasHeader = true)
  private val submissionLayoutWithHeader = userSubmissionSectionScrollLayout(hasHeader = true)

  @Test
  fun shouldStickTabsWhenHeaderVisible() {
    assertFalse(
        shouldStickUserSectionTabs(
            firstVisibleItemIndex = 1,
            firstVisibleItemScrollOffset = 0,
            hasHeader = true,
        )
    )
    assertTrue(
        shouldStickUserSectionTabs(
            firstVisibleItemIndex = 1,
            firstVisibleItemScrollOffset = 1,
            hasHeader = true,
        )
    )
  }

  @Test
  fun shouldStickTabsWhenHeaderHidden() {
    assertFalse(
        shouldStickUserSectionTabs(
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
            hasHeader = false,
        )
    )
    assertTrue(
        shouldStickUserSectionTabs(
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 1,
            hasHeader = false,
        )
    )
  }

  @Test
  fun selectingCurrentTabAtTopRefreshesOnly() {
    var refreshCalls = 0
    var scrollToTopCalls = 0
    var selectedRoute: UserChildRoute? = null

    handleUserSectionTabSelection(
        targetRoute = UserChildRoute.Gallery,
        currentRoute = UserChildRoute.Gallery,
        isAtTop = true,
        onSelectRoute = { selectedRoute = it },
        onRefreshCurrentRoute = { refreshCalls += 1 },
        onScrollCurrentRouteToTop = { scrollToTopCalls += 1 },
    )

    assertEquals(1, refreshCalls)
    assertEquals(0, scrollToTopCalls)
    assertEquals(null, selectedRoute)
  }

  @Test
  fun selectingCurrentTabAwayFromTopScrollsOnly() {
    var refreshCalls = 0
    var scrollToTopCalls = 0
    var selectedRoute: UserChildRoute? = null

    handleUserSectionTabSelection(
        targetRoute = UserChildRoute.Favorites,
        currentRoute = UserChildRoute.Favorites,
        isAtTop = false,
        onSelectRoute = { selectedRoute = it },
        onRefreshCurrentRoute = { refreshCalls += 1 },
        onScrollCurrentRouteToTop = { scrollToTopCalls += 1 },
    )

    assertEquals(0, refreshCalls)
    assertEquals(1, scrollToTopCalls)
    assertEquals(null, selectedRoute)
  }

  @Test
  fun selectingOtherTabRoutesOnly() {
    var refreshCalls = 0
    var scrollToTopCalls = 0
    var selectedRoute: UserChildRoute? = null

    handleUserSectionTabSelection(
        targetRoute = UserChildRoute.Journals,
        currentRoute = UserChildRoute.Gallery,
        isAtTop = false,
        onSelectRoute = { selectedRoute = it },
        onRefreshCurrentRoute = { refreshCalls += 1 },
        onScrollCurrentRouteToTop = { scrollToTopCalls += 1 },
    )

    assertEquals(0, refreshCalls)
    assertEquals(0, scrollToTopCalls)
    assertEquals(UserChildRoute.Journals, selectedRoute)
  }

  @Test
  fun switchingTabsAtTopKeepsInlineTopAndBodyAtStart() {
    val result =
        resolveInitialUserScrollPosition(
            sharedTopScrollState =
                resolveUserSharedTopScrollState(
                    firstVisibleItemIndex = 0,
                    firstVisibleItemScrollOffset = 0,
                    layout = layoutWithHeader,
                ),
            bodyScrollPosition = null,
            layout = layoutWithHeader,
        )

    assertEquals(0, result.firstVisibleItemIndex)
    assertEquals(0, result.firstVisibleItemScrollOffset)
    assertEquals(null, result.deferredBodyScrollPosition)
  }

  @Test
  fun switchingTabsWhileHeaderIsPartiallyScrolledKeepsExactInlineOffset() {
    val result =
        resolveInitialUserScrollPosition(
            sharedTopScrollState =
                resolveUserSharedTopScrollState(
                    firstVisibleItemIndex = 0,
                    firstVisibleItemScrollOffset = 72,
                    layout = layoutWithHeader,
                ),
            bodyScrollPosition =
                UserBodyScrollPosition(
                    firstVisibleItemIndex = 4,
                    firstVisibleItemScrollOffset = 18,
                ),
            layout = layoutWithHeader,
        )

    assertEquals(0, result.firstVisibleItemIndex)
    assertEquals(72, result.firstVisibleItemScrollOffset)
    assertEquals(
        UserBodyScrollPosition(firstVisibleItemIndex = 4, firstVisibleItemScrollOffset = 18),
        result.deferredBodyScrollPosition,
    )
  }

  @Test
  fun switchingFromStickyTabToFreshTabKeepsStickyButStartsBodyAtTop() {
    val result =
        resolveInitialUserScrollPosition(
            sharedTopScrollState = UserSharedTopScrollState.Sticky,
            bodyScrollPosition = null,
            layout = layoutWithHeader,
        )

    assertEquals(layoutWithHeader.tabsInlineIndex, result.firstVisibleItemIndex)
    assertEquals(3, result.firstVisibleItemScrollOffset)
    assertEquals(null, result.deferredBodyScrollPosition)
  }

  @Test
  fun inlineSharedTopDoesNotClearExistingBodyMemory() {
    val rememberedBody =
        UserBodyScrollPosition(firstVisibleItemIndex = 6, firstVisibleItemScrollOffset = 24)

    val result =
        resolveInitialUserScrollPosition(
            sharedTopScrollState =
                UserSharedTopScrollState.Inline(
                    firstVisibleItemIndex = 0,
                    firstVisibleItemScrollOffset = 0,
                ),
            bodyScrollPosition = rememberedBody,
            layout = layoutWithHeader,
        )

    assertEquals(0, result.firstVisibleItemIndex)
    assertEquals(0, result.firstVisibleItemScrollOffset)
    assertEquals(rememberedBody, result.deferredBodyScrollPosition)
  }

  @Test
  fun stickyStateRestoresRememberedBodyScroll() {
    val rememberedBody =
        UserBodyScrollPosition(firstVisibleItemIndex = 3, firstVisibleItemScrollOffset = 15)

    val result =
        resolveInitialUserScrollPosition(
            sharedTopScrollState = UserSharedTopScrollState.Sticky,
            bodyScrollPosition = rememberedBody,
            layout = layoutWithHeader,
        )

    assertEquals(layoutWithHeader.bodyStartIndex + 3, result.firstVisibleItemIndex)
    assertEquals(15, result.firstVisibleItemScrollOffset)
    assertEquals(null, result.deferredBodyScrollPosition)
  }

  @Test
  fun stickyStateWithZeroBodyOffsetStartsAtStickyTabsInsteadOfUnderThem() {
    val result =
        resolveInitialUserScrollPosition(
            sharedTopScrollState = UserSharedTopScrollState.Sticky,
            bodyScrollPosition = UserBodyScrollPosition(),
            layout = layoutWithHeader,
        )

    assertEquals(layoutWithHeader.tabsInlineIndex, result.firstVisibleItemIndex)
    assertEquals(3, result.firstVisibleItemScrollOffset)
  }

  @Test
  fun folderGroupsBelongToBodyInsteadOfSharedTop() {
    val topState =
        resolveUserSharedTopScrollState(
            firstVisibleItemIndex = submissionLayoutWithHeader.bodyStartIndex,
            firstVisibleItemScrollOffset = 0,
            layout = submissionLayoutWithHeader,
        )
    val bodyPosition =
        resolveUserBodyScrollPosition(
            firstVisibleItemIndex = submissionLayoutWithHeader.bodyStartIndex,
            firstVisibleItemScrollOffset = 0,
            layout = submissionLayoutWithHeader,
        )

    assertEquals(UserSharedTopScrollState.Sticky, topState)
    assertEquals(UserBodyScrollPosition(), bodyPosition)
  }
}
