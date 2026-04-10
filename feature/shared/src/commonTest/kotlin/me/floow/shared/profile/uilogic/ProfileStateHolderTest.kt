package me.floow.shared.profile.uilogic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileStateHolderTest {

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
}
