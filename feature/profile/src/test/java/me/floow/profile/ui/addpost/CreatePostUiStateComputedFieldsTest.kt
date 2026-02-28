package me.floow.profile.ui.addpost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatePostUiStateComputedFieldsTest {
	@Test
	fun computedFields_reflectSelectedImagesAndCategory() {
		val uiState = CreatePostUiState(
			description = "",
			selectedImages = listOf(
				CreatePostImageItem(id = "1", uri = "content://image/1")
			),
			categories = listOf(
				CreatePostCategoryItem(code = "music", title = "#music")
			),
			selectedCategoryCode = "music",
			isCategoriesLoading = false,
			isCategoriesError = false,
			isPublishing = false,
			publishingStage = null,
			uploadProgress = 0,
			uploadTotal = 0,
			errorMessage = null,
			canPublish = true
		)

		assertTrue(uiState.hasImages)
		assertTrue(uiState.canAddMoreImages)
		assertEquals("#music", uiState.selectedCategoryTitle)
	}

	@Test
	fun hasDraft_falseWhenAllFieldsEmpty() {
		val uiState = CreatePostUiState(
			description = "",
			selectedImages = emptyList(),
			categories = emptyList(),
			selectedCategoryCode = null,
			isCategoriesLoading = false,
			isCategoriesError = false,
			isPublishing = false,
			publishingStage = null,
			uploadProgress = 0,
			uploadTotal = 0,
			errorMessage = null,
			canPublish = false
		)

		assertFalse(uiState.hasDraft)
	}
}
