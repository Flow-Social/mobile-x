package me.floow.data.repos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.floow.domain.api.CategoriesApi
import me.floow.domain.api.models.GetCategoriesResponse
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.cache.CachePolicy
import me.floow.domain.data.cache.CacheState
import me.floow.domain.data.orFallback
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.models.CategoryCatalogItem
import me.floow.domain.utils.CacheDecision
import me.floow.domain.utils.Logger
import me.floow.domain.utils.TelemetryOperation
import me.floow.domain.utils.TelemetryScope
import me.floow.domain.utils.currentTimeMillis
import me.floow.domain.utils.logCacheDecision
import me.floow.domain.utils.logCacheState
import me.floow.domain.utils.logLoadTiming

class CategoryCatalogRepositoryImpl(
	private val logger: Logger,
	private val categoriesApi: CategoriesApi,
) : CategoryCatalogRepository {
	private companion object {
		private const val CATEGORIES_MAX_AGE_MS = 5 * 60 * 1000L
		private const val CATEGORIES_STALE_WHILE_REVALIDATE_MS = 30 * 60 * 1000L
	}

	private val categoriesState = MutableStateFlow<List<CategoryCatalogItem>>(emptyList())
	override val categoriesFlow: StateFlow<List<CategoryCatalogItem>> = categoriesState.asStateFlow()
	private val cachePolicy = CachePolicy(
		maxAgeMs = CATEGORIES_MAX_AGE_MS,
		staleWhileRevalidateMs = CATEGORIES_STALE_WHILE_REVALIDATE_MS
	)
	private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val refreshMutex = Mutex()
	private var lastUpdatedAtMs: Long? = null
	private var backgroundRefreshJob: Job? = null

	override suspend fun getOrRefresh(): GetDataResponse<List<CategoryCatalogItem>> {
		val cached = categoriesState.value
		val cache = cachePolicy.evaluate(
			hasCachedData = cached.isNotEmpty(),
			lastUpdatedAtMs = lastUpdatedAtMs
		)
		return when (cache.state) {
			CacheState.Empty -> refreshFromNetwork()
			CacheState.Fresh -> {
				logger.logCacheState(
					tag = "CategoryCatalogRepositoryImpl.getOrRefresh",
					scope = TelemetryScope.Categories,
					cache = cache
				)
				GetDataResponse.Success(cached)
			}
			CacheState.Stale -> {
				logger.logCacheState(
					tag = "CategoryCatalogRepositoryImpl.getOrRefresh",
					scope = TelemetryScope.Categories,
					cache = cache
				)
				scheduleBackgroundRefresh()
				GetDataResponse.Success(cached)
			}
			CacheState.Expired -> {
				logger.logCacheState(
					tag = "CategoryCatalogRepositoryImpl.getOrRefresh",
					scope = TelemetryScope.Categories,
					cache = cache,
					expiredDecision = CacheDecision.ExpiredRefresh
				)
				refreshFromNetwork()
			}
		}
	}

	override suspend fun refresh(force: Boolean): GetDataResponse<List<CategoryCatalogItem>> {
		if (force) {
			return refreshFromNetwork()
		}
		return getOrRefresh()
	}

	private suspend fun refreshFromNetwork(): GetDataResponse<List<CategoryCatalogItem>> {
		val startedAt = currentTimeMillis()
		return refreshMutex.withLock {
			when (val response = categoriesApi.getCategories()) {
				is GetCategoriesResponse.Success -> {
					val mapped = response.categories.map { item ->
						CategoryCatalogItem(
							code = item.code,
							isSystem = item.isSystem,
							postsCount = item.postsCount
						)
					}
					categoriesState.value = mapped
					lastUpdatedAtMs = currentTimeMillis()
					logger.logLoadTiming(
						tag = "CategoryCatalogRepositoryImpl.refresh",
						operation = TelemetryOperation.CategoriesNetworkRefresh,
						startedAtMs = startedAt,
						extras = "size=${mapped.size}"
					)
					GetDataResponse.Success(mapped)
				}
				GetCategoriesResponse.Error -> {
					logger.d("CategoryCatalogRepositoryImpl.refresh", "Failed to load categories from API")
					GetDataResponse.Error<List<CategoryCatalogItem>>(GetDataError.Other)
						.orFallback(categoriesState.value.takeIf { it.isNotEmpty() })
				}
			}
		}
	}

	private fun scheduleBackgroundRefresh() {
		if (backgroundRefreshJob?.isActive == true) return
		logger.logCacheDecision(
			tag = "CategoryCatalogRepositoryImpl.getOrRefresh",
			scope = TelemetryScope.Categories,
			decision = CacheDecision.BackgroundRevalidateScheduled
		)
		backgroundRefreshJob = repoScope.launch {
			refreshFromNetwork()
		}
	}
}
