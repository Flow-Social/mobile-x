package me.floow.shared.profile.uilogic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.floow.domain.models.UserPresence
import me.floow.shared.profile.ui.model.ProfilePostItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileStateHolderTest {

    @Test
    fun `resolved presence overrides payload values for foreign profile`() {
        val state = ProfileScreenState.Success(
            id = "user-1",
            shortUsername = "alice",
            avatarUri = null,
            backgroundUri = null,
            displayName = "Alice",
            description = null,
            totalLikesReceived = 0,
            isSelf = false,
            isOnline = false,
            lastSeenAtMillis = 100L,
            posts = emptyList(),
            arePostsLoading = false,
            arePostsError = false,
            canLoadMorePosts = false,
            isLoadingMorePosts = false,
        )

        val resolved = state.withResolvedPresence(
            UserPresence(
                userId = "user-1",
                isOnline = true,
                lastSeenAtMillis = 200L,
                serverTimestampMillis = 1L,
            )
        )

        assertTrue(resolved.isOnline)
        assertEquals(200L, resolved.lastSeenAtMillis)
    }

    @Test
    fun `missing live presence falls back to offline but keeps last seen`() {
        val state = ProfileScreenState.Success(
            id = "user-1",
            shortUsername = "alice",
            avatarUri = null,
            backgroundUri = null,
            displayName = "Alice",
            description = null,
            totalLikesReceived = 0,
            isSelf = false,
            isOnline = true,
            lastSeenAtMillis = 123L,
            posts = emptyList(),
            arePostsLoading = false,
            arePostsError = false,
            canLoadMorePosts = false,
            isLoadingMorePosts = false,
        )

        val resolved = state.withResolvedPresence(null)

        assertFalse(resolved.isOnline)
        assertEquals(123L, resolved.lastSeenAtMillis)
    }

    @Test
    fun `profile post screen model keeps commenters preview`() {
        val state = ProfileScreenState.Success(
            id = "user-1",
            shortUsername = "alice",
            avatarUri = "https://example.com/alice.jpg",
            backgroundUri = null,
            displayName = "Alice",
            description = null,
            totalLikesReceived = 0,
            isSelf = false,
            posts = listOf(
                ProfilePostItem(
                    id = "post-1",
                    description = "hello",
                    lqUrl = null,
                    previewUrl = "https://example.com/post-preview.jpg",
                    fullUrl = "https://example.com/post.jpg",
                    viewerUrls = listOf("https://example.com/post.jpg"),
                    previewUrls = listOf("https://example.com/post-preview.jpg"),
                    likesCount = 5,
                    commentsCount = 2,
                    commentersPreview = listOf("https://example.com/u1.jpg", "Bob"),
                ),
            ),
            arePostsLoading = false,
            arePostsError = false,
            canLoadMorePosts = false,
            isLoadingMorePosts = false,
        )

        val model = state.toPostScreenModel(postId = "post-1", sourceSnapshot = null)

        assertEquals(listOf("https://example.com/u1.jpg", "Bob"), model?.commentersPreview)
    }

    @Test
    fun `load keeps profile header visible when initial posts load failed`() = runTest {
        val holder = ProfileStateHolder(
            repository = object : ProfileRepository {
                override suspend fun getProfile(userId: String?): ProfilePayload {
                    return ProfilePayload(
                        id = "foreign-user",
                        shortUsername = "foreign",
                        avatarUrl = "https://example.com/avatar.jpg",
                        backgroundUrl = null,
                        displayName = "Foreign User",
                        description = "Bio",
                        totalLikesReceived = 7,
                        isSelf = false,
                        isOnline = true,
                        lastSeenAtMillis = 1234L,
                        posts = emptyList(),
                        arePostsError = true,
                        canLoadMorePosts = false,
                    )
                }

                override suspend fun getMorePosts(userId: String?, offset: Int, limit: Int): List<ProfilePost> {
                    return emptyList()
                }

                override suspend fun deletePost(postId: String): Result<Unit> {
                    return Result.success(Unit)
                }
            },
            userId = "foreign-user",
            scope = this,
        )

        holder.loadIfNeeded()
        advanceUntilIdle()

        val state = assertIs<ProfileScreenState.Success>(holder.state.value)
        assertEquals("foreign-user", state.id)
        assertEquals("foreign", state.shortUsername)
        assertEquals("Foreign User", state.displayName)
        assertTrue(state.arePostsError)
        assertTrue(state.posts.isEmpty())
        assertFalse(state.canLoadMorePosts)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `load shows cached profile seed before remote refresh`() = runTest {
        val remoteGate = CompletableDeferred<Unit>()
        val holder = ProfileStateHolder(
            repository = object : ProfileRepository {
                override suspend fun loadCachedInitial(userId: String?): ProfilePayload? {
                    return ProfilePayload(
                        id = "foreign-user",
                        shortUsername = "foreign",
                        avatarUrl = "https://example.com/avatar.jpg",
                        backgroundUrl = null,
                        displayName = "Foreign User",
                        description = "Cached bio",
                        totalLikesReceived = 7,
                        isSelf = false,
                        isOnline = false,
                        lastSeenAtMillis = null,
                        posts = listOf(
                            ProfilePost(
                                id = "cached-post",
                                description = "Cached post",
                                imageVariants = emptyList(),
                                imageUrls = emptyList(),
                                likesCount = 3,
                                commentsCount = 1,
                            ),
                        ),
                        arePostsError = false,
                        canLoadMorePosts = true,
                    )
                }

                override suspend fun getProfile(userId: String?): ProfilePayload {
                    remoteGate.await()
                    return ProfilePayload(
                        id = "foreign-user",
                        shortUsername = "foreign",
                        avatarUrl = "https://example.com/avatar.jpg",
                        backgroundUrl = null,
                        displayName = "Foreign User",
                        description = "Fresh bio",
                        totalLikesReceived = 8,
                        isSelf = false,
                        isOnline = true,
                        lastSeenAtMillis = 9999L,
                        posts = emptyList(),
                        arePostsError = false,
                        canLoadMorePosts = false,
                    )
                }

                override suspend fun getMorePosts(userId: String?, offset: Int, limit: Int): List<ProfilePost> {
                    return emptyList()
                }

                override suspend fun deletePost(postId: String): Result<Unit> {
                    return Result.success(Unit)
                }
            },
            userId = "foreign-user",
            scope = this,
        )

        holder.loadIfNeeded()
        advanceUntilIdle()

        val seededState = assertIs<ProfileScreenState.Success>(holder.state.value)
        assertEquals("cached-post", seededState.posts.first().id)
        assertEquals("Cached bio", seededState.description)
        assertFalse(seededState.arePostsError)

        remoteGate.complete(Unit)
        advanceUntilIdle()

        val refreshedState = assertIs<ProfileScreenState.Success>(holder.state.value)
        assertEquals("Fresh bio", refreshedState.description)
        assertEquals(8, refreshedState.totalLikesReceived)
        assertTrue(refreshedState.isOnline)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `loadIfNeeded keeps loaded profile without immediate remote refresh`() = runTest {
        var remoteLoads = 0
        val holder = ProfileStateHolder(
            repository = object : ProfileRepository {
                override suspend fun getProfile(userId: String?): ProfilePayload {
                    remoteLoads += 1
                    return ProfilePayload(
                        id = "foreign-user",
                        shortUsername = "foreign",
                        avatarUrl = null,
                        backgroundUrl = null,
                        displayName = "Foreign User",
                        description = "Fresh bio $remoteLoads",
                        totalLikesReceived = remoteLoads,
                        isSelf = false,
                        isOnline = true,
                        lastSeenAtMillis = null,
                        posts = emptyList(),
                        arePostsError = false,
                        canLoadMorePosts = false,
                    )
                }

                override suspend fun getMorePosts(userId: String?, offset: Int, limit: Int): List<ProfilePost> {
                    return emptyList()
                }

                override suspend fun deletePost(postId: String): Result<Unit> {
                    return Result.success(Unit)
                }
            },
            userId = "foreign-user",
            scope = this,
        )

        holder.loadIfNeeded()
        advanceUntilIdle()
        holder.loadIfNeeded()
        advanceUntilIdle()

        val state = assertIs<ProfileScreenState.Success>(holder.state.value)
        assertEquals("Fresh bio 1", state.description)
        assertEquals(1, remoteLoads)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `self profile state is initialized synchronously from hot snapshot`() = runTest {
        val hotPayload = ProfilePayload(
            id = "self-user-id",
            shortUsername = "selfuser",
            avatarUrl = "https://example.com/avatar.jpg",
            backgroundUrl = null,
            displayName = "Self User",
            description = "My hot bio",
            totalLikesReceived = 42,
            isSelf = true,
            isOnline = true,
            lastSeenAtMillis = null,
            posts = emptyList(),
            arePostsError = false,
            canLoadMorePosts = false,
        )
        val holder = ProfileStateHolder(
            repository = object : ProfileRepository {
                override fun observeSelfProfile() = MutableStateFlow<ProfilePayload?>(hotPayload)
                override suspend fun getProfile(userId: String?): ProfilePayload = hotPayload
                override suspend fun getMorePosts(userId: String?, offset: Int, limit: Int): List<ProfilePost> = emptyList()
                override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
            },
            userId = null,
            scope = this,
        )

        // Immediately without advanceUntilIdle:
        val immediateState = assertIs<ProfileScreenState.Success>(holder.state.value)
        assertEquals("self-user-id", immediateState.id)
        assertEquals("Self User", immediateState.displayName)
        assertTrue(immediateState.isSelf)
        assertFalse(immediateState.arePostsLoading)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `foreign profile state is initialized synchronously from header seed`() = runTest {
        val seed = ProfileHeaderSeed(
            userId = "bob-id",
            displayName = "Bob",
            username = "bob123",
            avatarUrl = "https://example.com/bob.jpg",
            avatarThumb = "https://example.com/bob_thumb.jpg",
        )
        val holder = ProfileStateHolder(
            repository = object : ProfileRepository {
                override fun getHeaderSeed(userId: String): ProfileHeaderSeed? = if (userId == "bob-id") seed else null
                override suspend fun getProfile(userId: String?): ProfilePayload {
                    return ProfilePayload(
                        id = "bob-id",
                        shortUsername = "bob123",
                        avatarUrl = "https://example.com/bob.jpg",
                        avatarThumb = "https://example.com/bob_thumb.jpg",
                        backgroundUrl = null,
                        displayName = "Bob",
                        description = "Full bio",
                        totalLikesReceived = 10,
                        isSelf = false,
                        isOnline = true,
                        lastSeenAtMillis = null,
                        posts = emptyList(),
                        arePostsError = false,
                        canLoadMorePosts = false,
                    )
                }
                override suspend fun getMorePosts(userId: String?, offset: Int, limit: Int): List<ProfilePost> = emptyList()
                override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
            },
            userId = "bob-id",
            scope = this,
        )

        // Immediately without waiting for network:
        val immediateState = assertIs<ProfileScreenState.Success>(holder.state.value)
        assertEquals("bob-id", immediateState.id)
        assertEquals("Bob", immediateState.displayName)
        assertEquals("bob123", immediateState.shortUsername)
        assertEquals("https://example.com/bob.jpg", immediateState.avatarUri)
        assertEquals("https://example.com/bob_thumb.jpg", immediateState.avatarThumb)
        assertTrue(immediateState.arePostsLoading) // Header is rendered, posts section is loading
        assertFalse(immediateState.isSelf)
        coroutineContext.cancelChildren()
    }
}
