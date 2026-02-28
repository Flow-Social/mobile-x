package me.floow.mock.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.models.CategoryCatalogItem
import me.floow.domain.models.PostCategories

class MockCategoryCatalogRepository : CategoryCatalogRepository {
	private val initial = PostCategories.defaults.map { code ->
		CategoryCatalogItem(
			code = code,
			isSystem = true,
			postsCount = 0
		)
	}

	private val state = MutableStateFlow(initial)
	override val categoriesFlow: StateFlow<List<CategoryCatalogItem>> = state

	override suspend fun getOrRefresh(): GetDataResponse<List<CategoryCatalogItem>> {
		return GetDataResponse.Success(state.value)
	}

	override suspend fun refresh(force: Boolean): GetDataResponse<List<CategoryCatalogItem>> {
		return GetDataResponse.Success(state.value)
	}
}
