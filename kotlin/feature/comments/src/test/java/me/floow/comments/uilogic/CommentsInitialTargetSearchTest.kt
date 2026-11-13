package me.floow.comments.uilogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentsInitialTargetSearchTest {

	@Test
	fun `advanceInitialTargetSearch resolves when target is present`() {
		val state = InitialTargetSearchState(
			candidates = listOf(541L, 530L),
			activeIndex = 0,
			loadAttempts = 2,
			isResolved = false
		)

		val (nextState, action) = advanceInitialTargetSearch(
			state = state,
			snapshot = InitialTargetSearchSnapshot(
				canLoadMore = true,
				isLoadingMore = false,
				isTargetPresent = true
			)
		)

		assertTrue(nextState.isResolved)
		assertEquals(InitialTargetSearchAction.NoOp, action)
	}

	@Test
	fun `advanceInitialTargetSearch requests load more when there are more pages`() {
		val state = InitialTargetSearchState(
			candidates = listOf(541L),
			activeIndex = 0,
			loadAttempts = 0,
			isResolved = false
		)

		val (nextState, action) = advanceInitialTargetSearch(
			state = state,
			snapshot = InitialTargetSearchSnapshot(
				canLoadMore = true,
				isLoadingMore = false,
				isTargetPresent = false
			)
		)

		assertEquals(1, nextState.loadAttempts)
		assertEquals(InitialTargetSearchAction.LoadMore, action)
	}

	@Test
	fun `advanceInitialTargetSearch waits while loading more is in progress`() {
		val state = InitialTargetSearchState(
			candidates = listOf(541L),
			activeIndex = 0,
			loadAttempts = 3,
			isResolved = false
		)

		val (nextState, action) = advanceInitialTargetSearch(
			state = state,
			snapshot = InitialTargetSearchSnapshot(
				canLoadMore = true,
				isLoadingMore = true,
				isTargetPresent = false
			)
		)

		assertEquals(state, nextState)
		assertEquals(InitialTargetSearchAction.NoOp, action)
	}

	@Test
	fun `advanceInitialTargetSearch switches to fallback target when primary is missing`() {
		val state = InitialTargetSearchState(
			candidates = listOf(541L, 530L),
			activeIndex = 0,
			loadAttempts = 8,
			isResolved = false
		)

		val (nextState, action) = advanceInitialTargetSearch(
			state = state,
			snapshot = InitialTargetSearchSnapshot(
				canLoadMore = false,
				isLoadingMore = false,
				isTargetPresent = false
			)
		)

		assertEquals(1, nextState.activeIndex)
		assertEquals(0, nextState.loadAttempts)
		assertTrue(!nextState.isResolved)
		assertEquals(InitialTargetSearchAction.ReloadWithAnchor(anchorCommentId = 530L), action)
	}

	@Test
	fun `advanceInitialTargetSearch resolves when target is missing and no fallback exists`() {
		val state = InitialTargetSearchState(
			candidates = listOf(541L),
			activeIndex = 0,
			loadAttempts = 8,
			isResolved = false
		)

		val (nextState, action) = advanceInitialTargetSearch(
			state = state,
			snapshot = InitialTargetSearchSnapshot(
				canLoadMore = false,
				isLoadingMore = false,
				isTargetPresent = false
			)
		)

		assertTrue(nextState.isResolved)
		assertEquals(InitialTargetSearchAction.NoOp, action)
	}
}
