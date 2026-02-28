package me.floow.domain.data.repos

import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.data.GetDataResponse
import me.floow.domain.models.CategoryCatalogItem

interface CategoryCatalogRepository {
	val categoriesFlow: StateFlow<List<CategoryCatalogItem>>

	suspend fun getOrRefresh(): GetDataResponse<List<CategoryCatalogItem>>

	suspend fun refresh(force: Boolean = false): GetDataResponse<List<CategoryCatalogItem>>
}
