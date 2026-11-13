package me.floow.shared.feed.uilogic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.domain.cache.FeedStackSnapshot
import me.floow.domain.cache.FeedStackSnapshotLocalStore
import me.floow.domain.cache.FeedSyncCommand
import me.floow.domain.cache.FeedSyncLocalStore
import me.floow.domain.cache.PostsCacheKeys
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.models.CategoryCatalogItem
import me.floow.domain.models.FeedPost
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostCategories
import me.floow.domain.models.PostContent
import me.floow.domain.models.PublicProfile
import me.floow.domain.models.UserProfile
import me.floow.domain.utils.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SharedFeedStateHolderCacheTest {

    @Test
    fun `persist after append stores recommendation reason`() = runTest {
        val snapshotStore = FakeFeedStackSnapshotLocalStore()
        val holder = testHolder(
            feedRepository = QueueFeedRepository(
                FeedPost(testPost("post-1"), "FAVORITE_AUTHOR"),
            ),
            snapshotStore = snapshotStore,
        )

        holder.onFeedScreenVisible()
        advanceUntilIdle()

        val snapshot = snapshotStore.snapshotForSelf()
        assertEquals("👤 Любимый автор", snapshot?.items?.firstOrNull()?.recommendationReason)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `restore cached stack preserves item and visible recommendation reason`() = runTest {
        val snapshotStore = FakeFeedStackSnapshotLocalStore()
        snapshotStore.replaceSnapshot(
            userId = PostsCacheKeys.feedStackUserId(PostsCacheKeys.SELF_USER_ID),
            snapshot = FeedStackSnapshot(
                items = listOf(
                    testSnapshotItem(
                        postId = "cached-post",
                        recommendationReason = "👥 Похожие интересы",
                    ),
                ),
            ),
            updatedAt = me.floow.domain.utils.currentTimeMillis(),
        )
        val holder = testHolder(
            feedRepository = QueueFeedRepository(),
            snapshotStore = snapshotStore,
        )

        holder.onFeedScreenVisible()
        advanceUntilIdle()

        val state = assertIs<SharedFeedScreenState.Success>(holder.state.value)
        assertEquals("👥 Похожие интересы", state.recommendationReason)
        assertEquals("👥 Похожие интересы", state.feedItems.first().recommendationReason)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `older async persist cannot overwrite newer swipe state`() = runTest {
        val firstPersistStarted = CompletableDeferred<Unit>()
        val releaseFirstPersist = CompletableDeferred<Unit>()
        val snapshotStore = FakeFeedStackSnapshotLocalStore(
            onReplace = { snapshot ->
                if (snapshot.items.firstOrNull()?.postId == "post-1") {
                    firstPersistStarted.complete(Unit)
                    releaseFirstPersist.await()
                }
            },
        )
        val holder = testHolder(
            feedRepository = QueueFeedRepository(
                FeedPost(testPost("post-1"), "FAVORITE"),
                FeedPost(testPost("post-2"), "SIMILAR"),
            ),
            snapshotStore = snapshotStore,
        )

        holder.onFeedScreenVisible()
        firstPersistStarted.await()
        holder.onSwipeRight()
        releaseFirstPersist.complete(Unit)
        advanceUntilIdle()

        val cachedIds = snapshotStore.snapshotForSelf()?.items.orEmpty().map { it.postId }
        assertEquals(listOf("post-2"), cachedIds)
        coroutineContext.cancelChildren()
    }


    private fun TestScope.testHolder(
        feedRepository: FeedRepository,
        userProfileRepository: UserProfileRepository = FakeUserProfileRepository(),
        snapshotStore: FakeFeedStackSnapshotLocalStore,
    ): SharedFeedStateHolder {
        return SharedFeedStateHolder(
            logger = NoopLogger,
            feedRepository = feedRepository,
            categoryCatalogRepository = FakeCategoryCatalogRepository(),
            postsRepository = FakePostsRepository(),
            userProfileRepository = userProfileRepository,
            profileLocalStore = FakeProfileLocalStore(),
            postsLocalStore = FakePostsLocalStore(),
            feedStackSnapshotLocalStore = snapshotStore,
            authenticationManager = FakeAuthenticationManager(),
            feedSyncLocalStore = FakeFeedSyncLocalStore(),
            scope = this,
        )
    }
}

private class QueueFeedRepository(
    vararg posts: FeedPost,
) : FeedRepository {
    private val queuedPosts = ArrayDeque(posts.toList())

    override suspend fun getNextPost(): GetDataResponse<FeedPost> {
        return queuedPosts.removeFirstOrNull()
            ?.let { GetDataResponse.Success(it) }
            ?: GetDataResponse.Error(GetDataError.NoData)
    }

    override suspend fun recordSwipe(postId: String, isLiked: Boolean): UpdateDataResponse {
        return UpdateDataResponse.Success()
    }

    override suspend fun undoSwipe(postId: String): UpdateDataResponse {
        return UpdateDataResponse.Success()
    }

    override fun clearSession() = Unit
}

private class FakeFeedStackSnapshotLocalStore(
    private val onReplace: suspend (FeedStackSnapshot) -> Unit = {},
) : FeedStackSnapshotLocalStore {
    private val snapshots = mutableMapOf<String, FeedStackSnapshot>()
    private val updatedAt = mutableMapOf<String, Long>()

    fun snapshotForSelf(): FeedStackSnapshot? {
        return snapshots[PostsCacheKeys.feedStackUserId(PostsCacheKeys.SELF_USER_ID)]
    }

    override fun observeSnapshot(userId: String): Flow<FeedStackSnapshot?> = emptyFlow()

    override suspend fun getSnapshot(userId: String): FeedStackSnapshot? = snapshots[userId]

    override suspend fun getLastUpdatedAt(userId: String): Long? = updatedAt[userId]

    override suspend fun replaceSnapshot(userId: String, snapshot: FeedStackSnapshot, updatedAt: Long) {
        onReplace(snapshot)
        snapshots[userId] = snapshot
        this.updatedAt[userId] = updatedAt
    }

    override suspend fun clearUser(userId: String) {
        snapshots.remove(userId)
        updatedAt.remove(userId)
    }
}

private object NoopLogger : Logger {
    override fun d(tag: String?, message: String) = Unit
}

private class FakeCategoryCatalogRepository : CategoryCatalogRepository {
    override val categoriesFlow = MutableStateFlow<List<CategoryCatalogItem>>(emptyList())

    override suspend fun getOrRefresh(): GetDataResponse<List<CategoryCatalogItem>> {
        return GetDataResponse.Success(emptyList())
    }

    override suspend fun refresh(force: Boolean): GetDataResponse<List<CategoryCatalogItem>> {
        return GetDataResponse.Success(emptyList())
    }
}

private class FakePostsRepository : PostsRepository {
    override suspend fun getPostById(postId: String): GetDataResponse<Post> {
        return GetDataResponse.Success(testPost(postId))
    }

    override suspend fun getUserPosts(
        userId: String,
        forceNetwork: Boolean,
        limit: Int,
        offset: Int,
    ): GetDataResponse<List<Post>> {
        return GetDataResponse.Success(emptyList())
    }

    override suspend fun createPost(data: me.floow.domain.api.models.CreatePostData): UpdateDataResponse {
        return UpdateDataResponse.Success()
    }

    override suspend fun deletePost(postId: String): UpdateDataResponse {
        return UpdateDataResponse.Success()
    }

    override suspend fun updatePost(
        postId: String,
        description: String?,
        imageUrls: List<String>?,
        category: String?,
    ): UpdateDataResponse {
        return UpdateDataResponse.Success()
    }
}

private class FakeUserProfileRepository(
    private val resetResult: UpdateDataResponse = UpdateDataResponse.Failure(),
) : UserProfileRepository {
    override suspend fun getUserProfile(): GetDataResponse<UserProfile> {
        return GetDataResponse.Success(UserProfile())
    }

    override suspend fun updateUserProfile(profile: UserProfile): UpdateDataResponse {
        return UpdateDataResponse.Success()
    }

    override suspend fun getSimilarUsers(myProfile: UserProfile): GetDataResponse<List<String>> {
        return GetDataResponse.Success(emptyList())
    }

    override suspend fun getAuthorLikes(authorUsername: String): GetDataResponse<List<String>> {
        return GetDataResponse.Success(emptyList())
    }

    override suspend fun clearProfile(): UpdateDataResponse {
        return UpdateDataResponse.Success()
    }

    override suspend fun resetRecommendations(): UpdateDataResponse {
        return resetResult
    }
}

private class FakeProfileLocalStore : ProfileLocalStore {
    override fun observeProfile(userId: String): Flow<PublicProfile?> = emptyFlow()
    override suspend fun getProfile(userId: String): PublicProfile? = null
    override suspend fun getLastUpdatedAt(userId: String): Long? = null
    override suspend fun upsertProfile(userId: String, profile: PublicProfile, updatedAt: Long) = Unit
    override suspend fun clearUser(userId: String) = Unit
}

private class FakePostsLocalStore : PostsLocalStore {
    override fun observePosts(userId: String): Flow<List<Post>> = emptyFlow()
    override suspend fun getPosts(userId: String): List<Post> = emptyList()
    override suspend fun getLastUpdatedAt(userId: String): Long? = null
    override suspend fun replacePosts(userId: String, posts: List<Post>, updatedAt: Long) = Unit
    override suspend fun clearUser(userId: String) = Unit
    override suspend fun getPost(userId: String, postId: String): Post? = null
    override suspend fun upsertPost(userId: String, post: Post, updatedAt: Long) = Unit
    override suspend fun deletePost(userId: String, postId: String) = Unit
}

private class FakeAuthenticationManager : AuthenticationManager {
    override val authenticationStateFlow = MutableStateFlow<AuthState>(AuthState.NoIdToken)
    override suspend fun handleGoogleOAuthCode(code: String) = Unit
    override suspend fun getAuthTokenOrNull(): String? = null
    override fun getSelfUserIdOrNull(): String = PostsCacheKeys.SELF_USER_ID
    override fun saveSelfUserId(userId: String) = Unit
    override fun isSignedIn(): Boolean = true
    override fun hasPendingRegistration(): Boolean = false
    override fun getPendingRegistrationTokenOrNull(): String? = null
    override fun getPendingRegistrationInitialDataOrNull(): PendingRegistrationInitialData? = null
    override suspend fun startGoogleAuthentication() = Unit
    override suspend fun writeAuthToken(token: String) = Unit
    override suspend fun clearPendingRegistration() = Unit
    override suspend fun clearAuth() = Unit
}

private class FakeFeedSyncLocalStore : FeedSyncLocalStore {
    override suspend fun enqueueSwipe(userId: String, postId: String, isLiked: Boolean) = Unit
    override suspend fun enqueueUndo(userId: String, postId: String) = Unit
    override suspend fun peekNext(userId: String, nowMs: Long): FeedSyncCommand? = null
    override suspend fun nextAttemptAt(userId: String): Long? = null
    override suspend fun remove(id: Long) = Unit
    override suspend fun incrementAttempts(id: Long, nextAttemptAt: Long) = Unit
    override suspend fun pendingCount(userId: String): Int = 0
    override suspend fun clearUser(userId: String) = Unit
}

private fun testPost(id: String): Post {
    return Post(
        id = id,
        author = PostAuthor(
            id = "author-$id",
            name = null,
            username = null,
            avatarUrl = null,
        ),
        content = PostContent(
            imageUrls = listOf("https://example.com/$id.jpg"),
            description = "Post $id",
        ),
        category = PostCategories.ART,
        createdAt = id.hashCode().toLong(),
    )
}

private fun testSnapshotItem(
    postId: String,
    recommendationReason: String?,
): me.floow.domain.cache.FeedStackSnapshotItem {
    return me.floow.domain.cache.FeedStackSnapshotItem(
        postId = postId,
        authorId = "author-$postId",
        imageUrls = listOf("https://example.com/$postId.jpg"),
        description = "Post $postId",
        category = PostCategories.ART,
        createdAt = postId.hashCode().toLong(),
        recommendationReason = recommendationReason,
    )
}
