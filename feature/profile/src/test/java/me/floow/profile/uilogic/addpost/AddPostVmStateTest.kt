package me.floow.profile.uilogic.addpost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AddPostVmStateTest {
	@Test
	fun canPublish_requiresCategory() {
		val state = AddPostVmState(
			localImageUris = listOf("content://image/1"),
			selectedCategoryCode = null
		)

		assertFalse(state.canPublish(allowManualUrls = false))
	}

	@Test
	fun canPublish_requiresAtLeastOneImage() {
		val state = AddPostVmState(
			localImageUris = emptyList(),
			selectedCategoryCode = "music"
		)

		assertFalse(state.canPublish(allowManualUrls = false))
	}

	@Test
	fun canPublish_trueWhenCategoryAndImageSelected() {
		val state = AddPostVmState(
			localImageUris = listOf("content://image/1"),
			selectedCategoryCode = "music"
		)

		assertTrue(state.canPublish(allowManualUrls = false))
	}

	@Test
	fun canPublish_falseWhenImageLimitExceeded() {
		val state = AddPostVmState(
			localImageUris = List(AddPostVmState.MAX_IMAGES + 1) { "content://image/$it" },
			selectedCategoryCode = "music"
		)

		assertFalse(state.canPublish(allowManualUrls = false))
	}

	@Test
	fun reorderItems_movesElementToNewIndex() {
		val result = reorderItems(
			items = listOf("a", "b", "c", "d"),
			fromIndex = 1,
			toIndex = 3
		)

		assertEquals(listOf("a", "c", "d", "b"), result)
	}

	@Test
	fun reorderItems_returnsSameListForInvalidIndices() {
		val source = listOf("a", "b", "c")

		val result = reorderItems(
			items = source,
			fromIndex = -1,
			toIndex = 2
		)

		assertSame(source, result)
	}

	@Test
	fun reorderItems_returnsSameListWhenIndexNotChanged() {
		val source = listOf("a", "b", "c")

		val result = reorderItems(
			items = source,
			fromIndex = 2,
			toIndex = 2
		)

		assertSame(source, result)
	}
}
