package me.floow.profile.uilogic.profile

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.cache.UsernameToIdCache
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.cache.CachePolicy
import me.floow.domain.data.cache.CacheState
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.data.repos.UsersRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.models.Post
import me.floow.domain.models.PublicProfile
import me.floow.domain.models.UserPresence
import me.floow.domain.utils.Logger
import me.floow.domain.utils.TelemetryOperation
import me.floow.domain.utils.TelemetryScope
import me.floow.domain.utils.logCacheState
import me.floow.domain.utils.logLoadTiming

data class ProfileScreenVmState(
	val isLoading: Boolean = false,
	val isError: Boolean = false,
	val id: String = "",
	val shortUsername: String? = null,
	val avatarUrl: Uri? = null,
	val backgroundUrl: Uri? = null,
	val displayName: String? = null,
	val description: String? = null,
	val totalLikesReceived: Int = 0,
	val isSelf: Boolean = true,
	val isOnline: Boolean = false,
	val lastSeenAtMillis: Long? = null,
	val posts: List<Post> = emptyList(),
	val arePostsLoading: Boolean = false,
	val arePostsError: Boolean = false,
	val nextPostsOffset: Int = 0,
	val canLoadMorePosts: Boolean = false,
	val isLoadingMorePosts: Boolean = false,
) {
	fun toUiState(): ProfileScreenState {
		if (isLoading && !hasRenderableContent()) return ProfileScreenState.Loading

		if (isError && !hasRenderableContent()) return ProfileScreenState.Error

		return ProfileScreenState.Success(
			id = id,
			shortUsername = shortUsername,
			avatarUri = avatarUrl,
			backgroundUri = backgroundUrl,
			description = description,
			displayName = displayName,
			totalLikesReceived = totalLikesReceived,
			isSelf = isSelf,
			isOnline = isOnline,
			lastSeenAtMillis = lastSeenAtMillis,
			posts = posts,
			arePostsLoading = arePostsLoading,
			arePostsError = arePostsError,
			canLoadMorePosts = canLoadMorePosts,
			isLoadingMorePosts = isLoadingMorePosts,
		)
	}

	private fun hasRenderableContent(): Boolean {
		return id.isNotBlank() ||
			shortUsername != null ||
			avatarUrl != null ||
			backgroundUrl != null ||
			displayName != null ||
			description != null ||
			posts.isNotEmpty()
	}
}

class ProfileScreenViewModel(
	private val logger: Logger,
	private val profileRepository: ProfileRepository,
	private val postsRepository: PostsRepository,
	private val usersRepository: UsersRepository,
	private val presenceRepository: PresenceRepository,
	private val profileLocalStore: ProfileLocalStore,
	private val postsLocalStore: PostsLocalStore,
	private val usernameToIdCache: UsernameToIdCache,
	private val savedStateHandle: SavedStateHandle
) : ViewModel() {
	private companion object {
		private const val PROFILE_REMOTE_MAX_AGE_MS = 5 * 60 * 1000L
		private const val PROFILE_REMOTE_STALE_REVALIDATE_MS = 30 * 60 * 1000L
		private const val PROFILE_POSTS_PAGE_SIZE = 20
		private const val PROFILE_POSTS_CACHE_MAX_ITEMS = 120
		private const val PROFILE_POSTS_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
		private const val DELETED_POST_TOMBSTONE_TTL_MS = 60_000L
		private const val PRESENCE_OWNER_PREFIX = "profile:"
	}

	private val _state = MutableStateFlow(ProfileScreenVmState())

	val state = _state
		.map(ProfileScreenVmState::toUiState)
		.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileScreenState.Loading)

	private var cachedUserId: String? = null
	private var profileCacheJob: Job? = null
	private var postsCacheJob: Job? = null
	private var hasCachedProfile: Boolean = false
	private var hasCachedPosts: Boolean = false
	private var postsCachePrimed: Boolean = false
	private val deletedPostTombstones = mutableMapOf<String, Long>()
	private val hasReachedPostsEndByUser = mutableMapOf<String, Boolean>()
	private val remoteCachePolicy = CachePolicy(
		maxAgeMs = PROFILE_REMOTE_MAX_AGE_MS,
		staleWhileRevalidateMs = PROFILE_REMOTE_STALE_REVALIDATE_MS
	)
	private val lastRemoteFetchAtByUser = mutableMapOf<String, Long>()
	private var currentPresenceOwner: String? = null

	init {
		viewModelScope.launch {
			presenceRepository.presences.collectLatest { presences ->
				applyPresence(presences[_state.value.id])
			}
		}
	}

	fun loadData(forceRemote: Boolean = false) {
		val userId: String? = savedStateHandle["userId"]
		val isSelf = userId == null || userId == "me" || userId == "self"
		val targetUserId = if (isSelf) "me" else userId.orEmpty()

		_state.update { it.copy(isSelf = isSelf) }
		viewModelScope.launch {
			val startedAt = System.currentTimeMillis()
			val resolvedUserId = if (isSelf) {
				"me"
			} else if (targetUserId.all(Char::isDigit)) {
				targetUserId
			} else {
				resolveUserIdByUsername(targetUserId) ?: targetUserId
			}
			if (!isSelf && resolvedUserId.isNotBlank()) {
				bindPresenceToUser(resolvedUserId)
				applyPresence(presenceRepository.presences.value[resolvedUserId])
			}

			prunePostsCacheIfStale(resolvedUserId)
			startCacheObserversIfNeeded(resolvedUserId)

			val cachedProfileSnapshot = profileLocalStore.getProfile(resolvedUserId)
			val cachedPostsSnapshot = capProfileCachedPosts(
				filterDeletedPosts(postsLocalStore.getPosts(resolvedUserId))
			)
			hasCachedProfile = cachedProfileSnapshot != null
			hasCachedPosts = cachedPostsSnapshot.isNotEmpty()

			_state.update { current ->
				val seededPosts = if (cachedPostsSnapshot.isNotEmpty()) {
					cachedPostsSnapshot
				} else {
					current.posts
				}
				current.copy(
					id = cachedProfileSnapshot?.id ?: current.id,
					shortUsername = cachedProfileSnapshot?.username?.value ?: current.shortUsername,
					avatarUrl = cachedProfileSnapshot?.avatarUrl?.let(Uri::parse) ?: current.avatarUrl,
					backgroundUrl = cachedProfileSnapshot?.let { cached ->
						cacheBustedUri(
							url = cached.backgroundUrl,
							updatedAt = cached.backgroundUpdatedAt
						)
					} ?: current.backgroundUrl,
					displayName = cachedProfileSnapshot?.name?.value ?: current.displayName,
					description = cachedProfileSnapshot?.description?.value ?: current.description,
					totalLikesReceived = cachedProfileSnapshot?.totalLikesReceived ?: current.totalLikesReceived,
					posts = seededPosts,
					isLoading = !hasCachedProfile && seededPosts.isEmpty(),
					arePostsLoading = seededPosts.isEmpty(),
					arePostsError = false,
					nextPostsOffset = seededPosts.size,
					canLoadMorePosts = canLoadMorePosts(
						userId = resolvedUserId,
						currentPostsCount = seededPosts.size
					),
					isLoadingMorePosts = false
				)
			}

			val persistedProfileUpdatedAt = profileLocalStore.getLastUpdatedAt(resolvedUserId)
			val persistedPostsUpdatedAt = postsLocalStore.getLastUpdatedAt(resolvedUserId)
			val persistedUpdatedAt = maxOfOrNull(persistedProfileUpdatedAt, persistedPostsUpdatedAt)
			val inMemoryUpdatedAt = lastRemoteFetchAtByUser[resolvedUserId]
			val effectiveUpdatedAt = maxOfOrNull(inMemoryUpdatedAt, persistedUpdatedAt)
			val hasCache = hasCachedProfile || hasCachedPosts
			val cache = remoteCachePolicy.evaluate(
				hasCachedData = hasCache,
				lastUpdatedAtMs = effectiveUpdatedAt
			)
			logger.logCacheState(
				tag = "ProfileScreenViewModel.loadData",
				scope = TelemetryScope.ProfileScreen,
				cache = cache,
				extras = "userId=$resolvedUserId"
			)
			if (cache.state == CacheState.Fresh && !forceRemote) {
				return@launch
			}

			val profileDeferred = async {
				if (isSelf) {
					val selfData = profileRepository.getSelfData()
					if (selfData is GetDataResponse.Success) {
						GetDataResponse.Success(
							PublicProfile(
								id = "me",
								name = selfData.data.name,
								username = selfData.data.username,
								avatarUrl = selfData.data.avatarUrl,
								backgroundUrl = selfData.data.backgroundUrl,
								backgroundUpdatedAt = selfData.data.backgroundUpdatedAt,
								description = selfData.data.description,
								totalLikesReceived = selfData.data.totalLikesReceived
							)
						)
					} else {
						GetDataResponse.Error(me.floow.domain.data.GetDataError.Other)
					}
				} else {
					profileRepository.getUserProfile(resolvedUserId)
				}
			}

			val postsDeferred = async {
				postsRepository.getUserPosts(
					userId = resolvedUserId,
					forceNetwork = forceRemote,
					limit = PROFILE_POSTS_PAGE_SIZE,
					offset = 0
				)
			}

			val profileResponse = profileDeferred.await()
			val postsResponse = postsDeferred.await()
			val remoteSuccess =
				profileResponse is GetDataResponse.Success && postsResponse is GetDataResponse.Success
			if (remoteSuccess) {
				lastRemoteFetchAtByUser[resolvedUserId] = System.currentTimeMillis()
			}

			when (profileResponse) {
				is GetDataResponse.Success -> {
					logger.d(
						"ProfileScreenViewModel loadData",
						"Success profile data response: $profileResponse"
					)

					profileLocalStore.upsertProfile(
						userId = resolvedUserId,
						profile = profileResponse.data,
						updatedAt = System.currentTimeMillis()
					)
					cacheKnownUser(
						username = profileResponse.data.username?.value,
						userId = resolvedUserId
					)
				}

				is GetDataResponse.Error -> {
					logger.d(
						"ProfileScreenViewModel loadData",
						"Failure profile data response: $profileResponse"
					)

					if (!hasCachedProfile) {
						_state.update {
							it.copy(
								isLoading = false,
								isError = true
							)
						}
					}
				}
			}

			when (postsResponse) {
				is GetDataResponse.Success -> {
					postsCachePrimed = true
					val pageItems = capProfileCachedPosts(filterDeletedPosts(postsResponse.data))
					val loadedCount = pageItems.size
					hasReachedPostsEndByUser[resolvedUserId] = loadedCount < PROFILE_POSTS_PAGE_SIZE
					_state.update {
						it.copy(
							posts = pageItems,
							isLoading = false,
							arePostsLoading = false,
							arePostsError = false,
							nextPostsOffset = loadedCount,
							canLoadMorePosts = canLoadMorePosts(
								userId = resolvedUserId,
								currentPostsCount = pageItems.size
							),
							isLoadingMorePosts = false
						)
					}
					postsLocalStore.replacePosts(
						userId = resolvedUserId,
						posts = pageItems,
						updatedAt = System.currentTimeMillis()
					)
					cacheKnownUsersFromPosts(pageItems)
				}
				is GetDataResponse.Error -> {
					if (!hasCachedPosts) {
						_state.update {
							it.copy(
								isLoading = false,
								arePostsLoading = false,
								arePostsError = true,
								isLoadingMorePosts = false
							)
						}
					}
				}
			}

			logger.logLoadTiming(
				tag = "ProfileScreenViewModel.loadData",
				operation = TelemetryOperation.ProfileLoad,
				startedAtMs = startedAt,
				extras = "userId=$resolvedUserId profileCached=$hasCachedProfile postsCached=$hasCachedPosts"
			)
		}
	}

	fun loadMorePosts() {
		val userId = cachedUserId ?: return
		val snapshot = _state.value
		if (snapshot.isLoading || snapshot.arePostsLoading) return
		if (snapshot.isLoadingMorePosts || !snapshot.canLoadMorePosts) return

		_state.update { it.copy(isLoadingMorePosts = true) }
		viewModelScope.launch {
			when (val response = postsRepository.getUserPosts(
				userId = userId,
				forceNetwork = false,
				limit = PROFILE_POSTS_PAGE_SIZE,
				offset = snapshot.nextPostsOffset
			)) {
				is GetDataResponse.Success -> {
					val incoming = filterDeletedPosts(response.data)
					val loadedCount = incoming.size
					val hasMore = loadedCount >= PROFILE_POSTS_PAGE_SIZE
					hasReachedPostsEndByUser[userId] = !hasMore

					_state.update { state ->
						val merged = (state.posts + incoming)
							.distinctBy { post -> post.id }
						val capped = capProfileCachedPosts(merged)
						state.copy(
							posts = capped,
							nextPostsOffset = state.nextPostsOffset + loadedCount,
							canLoadMorePosts = canLoadMorePosts(
								userId = userId,
								currentPostsCount = capped.size
							),
							isLoadingMorePosts = false,
							arePostsError = false
						)
					}

					postsCachePrimed = true
					postsLocalStore.replacePosts(
						userId = userId,
						posts = capProfileCachedPosts(_state.value.posts),
						updatedAt = System.currentTimeMillis()
					)
					cacheKnownUsersFromPosts(incoming)
				}

				is GetDataResponse.Error -> {
					_state.update { state ->
						state.copy(
							isLoadingMorePosts = false,
							arePostsError = state.posts.isEmpty()
						)
					}
				}
			}
		}
	}

	fun deletePost(postId: String) {
		viewModelScope.launch {
			val result = postsRepository.deletePost(postId)
			if (result is me.floow.domain.data.UpdateDataResponse.Success) {
				deletedPostTombstones[postId] = System.currentTimeMillis()
				val activeUserId = cachedUserId ?: return@launch
				val deletedPost = _state.value.posts.firstOrNull { post -> post.id == postId }
				val likesToSubtract = deletedPost?.likesCount ?: 0
				val updatedPosts = _state.value.posts.filter { post -> post.id != postId }
				val capped = capProfileCachedPosts(updatedPosts)
				_state.update { state ->
					state.copy(
						posts = capped,
						totalLikesReceived = (state.totalLikesReceived - likesToSubtract).coerceAtLeast(0),
						canLoadMorePosts = canLoadMorePosts(
							userId = activeUserId,
							currentPostsCount = capped.size
						)
					)
				}
				postsLocalStore.replacePosts(
					userId = activeUserId,
					posts = capped,
					updatedAt = System.currentTimeMillis()
				)
			}
		}
	}

	private fun startCacheObserversIfNeeded(targetUserId: String) {
		if (cachedUserId == targetUserId) return
		cachedUserId = targetUserId

		hasCachedProfile = false
		hasCachedPosts = false
		postsCachePrimed = false

		profileCacheJob?.cancel()
		postsCacheJob?.cancel()

		profileCacheJob = viewModelScope.launch {
			profileLocalStore.observeProfile(targetUserId).collect { cached ->
				if (cached == null) return@collect
				hasCachedProfile = true
				_state.update { state ->
					val nextAvatar = cached.avatarUrl?.let(Uri::parse)
					val nextBackground = cacheBustedUri(
						url = cached.backgroundUrl,
						updatedAt = cached.backgroundUpdatedAt
					)
					val unchanged = state.id == cached.id &&
						state.shortUsername == cached.username?.value &&
						state.avatarUrl == nextAvatar &&
						state.backgroundUrl == nextBackground &&
						state.displayName == cached.name?.value &&
						state.description == cached.description?.value &&
						state.totalLikesReceived == cached.totalLikesReceived &&
						!state.isLoading &&
						!state.isError
					if (unchanged) {
						state
					} else {
						state.copy(
							id = cached.id,
							shortUsername = cached.username?.value,
							avatarUrl = nextAvatar,
							backgroundUrl = nextBackground,
							displayName = cached.name?.value,
							description = cached.description?.value,
							totalLikesReceived = cached.totalLikesReceived,
							isLoading = false,
							isError = false
						)
					}
				}
			}
		}

		postsCacheJob = viewModelScope.launch {
			postsLocalStore.observePosts(targetUserId).collect { cachedPosts ->
				val cappedCachedPosts = capProfileCachedPosts(filterDeletedPosts(cachedPosts))
				val shouldApply = postsCachePrimed || cappedCachedPosts.isNotEmpty()
				if (!shouldApply) return@collect

				hasCachedPosts = true
				_state.update { state ->
					val nextOffset = if (state.nextPostsOffset == 0) {
						cappedCachedPosts.size
					} else {
						maxOf(state.nextPostsOffset, cappedCachedPosts.size)
					}
					val nextCanLoadMore = canLoadMorePosts(
						userId = targetUserId,
						currentPostsCount = cappedCachedPosts.size
					)
					val unchanged = state.posts == cappedCachedPosts &&
						!state.arePostsLoading &&
						!state.arePostsError &&
						state.nextPostsOffset == nextOffset &&
						state.canLoadMorePosts == nextCanLoadMore &&
						(!state.isLoading || cappedCachedPosts.isEmpty())
					if (unchanged) {
						state
					} else {
						state.copy(
							posts = cappedCachedPosts,
							arePostsLoading = false,
							arePostsError = false,
							nextPostsOffset = nextOffset,
							canLoadMorePosts = nextCanLoadMore,
							isLoading = state.isLoading && cappedCachedPosts.isEmpty()
						)
					}
				}
			}
		}
	}

	private suspend fun prunePostsCacheIfStale(userId: String) {
		val lastUpdatedAt = postsLocalStore.getLastUpdatedAt(userId) ?: return
		val ageMs = System.currentTimeMillis() - lastUpdatedAt
		if (ageMs <= PROFILE_POSTS_CACHE_TTL_MS) return
		postsLocalStore.clearUser(userId)
		hasReachedPostsEndByUser.remove(userId)
		hasCachedPosts = false
		postsCachePrimed = false
	}

	private fun canLoadMorePosts(userId: String, currentPostsCount: Int): Boolean {
		return when (hasReachedPostsEndByUser[userId]) {
			true -> false
			false -> true
			null -> currentPostsCount >= PROFILE_POSTS_PAGE_SIZE
		}
	}

	private fun capProfileCachedPosts(posts: List<Post>): List<Post> {
		if (posts.size <= PROFILE_POSTS_CACHE_MAX_ITEMS) return posts
		return posts.take(PROFILE_POSTS_CACHE_MAX_ITEMS)
	}

	fun onProfileScreenVisible() {
		val current = _state.value
		if (current.isSelf) return
		val userId = current.id.takeIf { it.isNotBlank() } ?: return
		bindPresenceToUser(userId)
	}

	fun onProfileScreenHidden() {
		clearPresenceBinding()
	}

	private fun applyPresence(presence: UserPresence?) {
		_state.update { state ->
			if (state.isSelf || state.id.isBlank()) return@update state
			if (presence == null) return@update state
			val isOnline = presence.isOnline
			val incomingLastSeen = presence.lastSeenAtMillis
			val lastSeen = when {
				isOnline -> incomingLastSeen ?: state.lastSeenAtMillis
				incomingLastSeen != null && incomingLastSeen > 0L -> incomingLastSeen
				else -> state.lastSeenAtMillis
			}
			if (state.isOnline == isOnline && state.lastSeenAtMillis == lastSeen) return@update state
			state.copy(isOnline = isOnline, lastSeenAtMillis = lastSeen)
		}
	}

	private fun bindPresenceToUser(userId: String) {
		val owner = PRESENCE_OWNER_PREFIX + userId
		if (currentPresenceOwner == owner) return
		currentPresenceOwner?.let(presenceRepository::clearTargets)
		currentPresenceOwner = owner
		presenceRepository.setTargets(owner = owner, userIds = listOf(userId))
	}

	private fun clearPresenceBinding() {
		currentPresenceOwner?.let(presenceRepository::clearTargets)
		currentPresenceOwner = null
	}

	override fun onCleared() {
		clearPresenceBinding()
		super.onCleared()
	}

	private fun filterDeletedPosts(posts: List<Post>): List<Post> {
		pruneExpiredDeletedPostTombstones()
		if (deletedPostTombstones.isEmpty()) return posts
		return posts.filterNot { post -> deletedPostTombstones.containsKey(post.id) }
	}

	private fun pruneExpiredDeletedPostTombstones(nowMs: Long = System.currentTimeMillis()) {
		deletedPostTombstones.entries.removeAll { (_, deletedAt) ->
			nowMs - deletedAt > DELETED_POST_TOMBSTONE_TTL_MS
		}
	}

	private fun cacheKnownUser(username: String?, userId: String) {
		val normalizedUserId = userId.trim()
		if (normalizedUserId.isEmpty()) return
		if (normalizedUserId == "me" || normalizedUserId == "self") return
		val normalizedUsername = username?.trim().orEmpty()
		if (normalizedUsername.isEmpty()) return
		usernameToIdCache.put(normalizedUsername, normalizedUserId)
	}

	private fun cacheKnownUsersFromPosts(posts: List<Post>) {
		posts.forEach { post ->
			cacheKnownUser(
				username = post.author.username?.value,
				userId = post.author.id
			)
		}
	}

	private suspend fun resolveUserIdByUsername(username: String): String? {
		val normalized = username.trim()
		usernameToIdCache.getUserId(normalized)?.let { cachedUserId ->
			return cachedUserId
		}
		val search = usersRepository.searchUsers(normalized)
		if (search is GetDataResponse.Success) {
			val exact = search.data.firstOrNull { user ->
				user.username?.value?.equals(normalized, ignoreCase = true) == true
			}
			val resolvedUserId = exact?.id
			if (!resolvedUserId.isNullOrBlank()) {
				usernameToIdCache.put(normalized, resolvedUserId)
			}
			return resolvedUserId
		}
		return null
	}
}

private fun cacheBustedUri(url: String?, updatedAt: Long?): Uri? {
	val normalized = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val version = updatedAt ?: return Uri.parse(normalized)
	val separator = if (normalized.contains("?")) "&" else "?"
	return Uri.parse("${normalized}${separator}v=${version}")
}

private fun maxOfOrNull(first: Long?, second: Long?): Long? {
	return when {
		first == null -> second
		second == null -> first
		else -> maxOf(first, second)
	}
}
