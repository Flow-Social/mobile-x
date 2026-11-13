package me.floow.domain.api.models

sealed interface GetCategoriesResponse {
	data class Success(
		val categories: List<CategoryItem>
	) : GetCategoriesResponse

	data object Error : GetCategoriesResponse
}

data class CategoryItem(
	val code: String,
	val isSystem: Boolean,
	val postsCount: Int,
)
