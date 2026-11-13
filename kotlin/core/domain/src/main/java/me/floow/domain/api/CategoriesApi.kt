package me.floow.domain.api

import me.floow.domain.api.models.GetCategoriesResponse

interface CategoriesApi {
	suspend fun getCategories(): GetCategoriesResponse
}
