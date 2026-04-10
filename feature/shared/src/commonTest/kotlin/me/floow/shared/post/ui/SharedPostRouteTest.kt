package me.floow.shared.post.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostContent
import me.floow.domain.models.PublicProfile

@OptIn(ExperimentalCoroutinesApi::class)
class SharedPostRouteTest {

    @Test
    fun `syncDeletedPostCaches removes post and decrements likes for me and author`() = runTest {
        val deletedPost = samplePost(id = "post-1", likesCount = 5)
        val keptPost = samplePost(id = "post-2", likesCount = 1)
        val postsStore = FakePostsLocalStore(
            mapOf(
                "me" to listOf(deletedPost, keptPost),
                "author-1" to listOf(deletedPost),
            ),
        )
        val profileStore = FakeProfileLocalStore(
            mapOf(
                "me" to PublicProfile(
                    id = "me",
                    name = null,
                    username = null,
                    avatarUrl = null,
                    backgroundUrl = null,
                    backgroundUpdatedAt = null,
                    description = null,
                    totalLikesReceived = 12,
                ),
                "author-1" to PublicProfile(
                    id = "author-1",
                    name = null,
                    username = null,
                    avatarUrl = null,
                    backgroundUrl = null,
                    backgroundUpdatedAt = null,
                    description = null,
                    totalLikesReceived = 7,
                ),
            ),
        )

        syncDeletedPostCaches(
            postId = "post-1",
            authorId = "author-1",
            postsLocalStore = postsStore,
            profileLocalStore = profileStore,
        )

        assertEquals(listOf(keptPost), postsStore.getPosts("me"))
        assertEquals(emptyList(), postsStore.getPosts("author-1"))
        assertEquals(7, profileStore.getProfile("me")?.totalLikesReceived)
        assertEquals(2, profileStore.getProfile("author-1")?.totalLikesReceived)
    }

    @Test
    fun `syncDeletedPostCaches leaves stores unchanged when post is absent`() = runTest {
        val keptPost = samplePost(id = "post-2", likesCount = 1)
        val postsStore = FakePostsLocalStore(
            mapOf("me" to listOf(keptPost)),
        )
        val profileStore = FakeProfileLocalStore(
            mapOf(
                "me" to PublicProfile(
                    id = "me",
                    name = null,
                    username = null,
                    avatarUrl = null,
                    backgroundUrl = null,
                    backgroundUpdatedAt = null,
                    description = null,
                    totalLikesReceived = 4,
                ),
            ),
        )

        syncDeletedPostCaches(
            postId = "missing",
            authorId = "author-1",
            postsLocalStore = postsStore,
            profileLocalStore = profileStore,
        )

        assertEquals(listOf(keptPost), postsStore.getPosts("me"))
        assertEquals(4, profileStore.getProfile("me")?.totalLikesReceived)
    }

    private fun samplePost(id: String, likesCount: Int): Post {
        return Post(
            id = id,
            author = PostAuthor(
                id = "author-1",
                name = null,
                username = null,
                avatarUrl = null,
            ),
            content = PostContent(
                imageUrls = listOf("https://example.com/$id.jpg"),
                description = "desc",
            ),
            category = "ART",
            createdAt = 1L,
            likesCount = likesCount,
            commentsCount = 0,
            commentersPreview = emptyList(),
        )
    }
}

private class FakePostsLocalStore(
    initial: Map<String, List<Post>>,
) : PostsLocalStore {
    private val flows = initial.mapValues { (_, posts) -> MutableStateFlow(posts) }.toMutableMap()

    override fun observePosts(userId: String): Flow<List<Post>> =
        flows.getOrPut(userId) { MutableStateFlow(emptyList()) }

    override suspend fun getPosts(userId: String): List<Post> = flows[userId]?.value.orEmpty()

    override suspend fun getLastUpdatedAt(userId: String): Long? = null

    override suspend fun replacePosts(userId: String, posts: List<Post>, updatedAt: Long) {
        flows.getOrPut(userId) { MutableStateFlow(emptyList()) }.value = posts
    }

    override suspend fun clearUser(userId: String) {
        flows.remove(userId)
    }
}

private class FakeProfileLocalStore(
    initial: Map<String, PublicProfile>,
) : ProfileLocalStore {
    private val flows = initial.mapValues { (_, profile) -> MutableStateFlow<PublicProfile?>(profile) }.toMutableMap()

    override fun observeProfile(userId: String): Flow<PublicProfile?> =
        flows.getOrPut(userId) { MutableStateFlow(null) }

    override suspend fun getProfile(userId: String): PublicProfile? = flows[userId]?.value

    override suspend fun getLastUpdatedAt(userId: String): Long? = null

    override suspend fun upsertProfile(userId: String, profile: PublicProfile, updatedAt: Long) {
        flows.getOrPut(userId) { MutableStateFlow(null) }.value = profile
    }

    override suspend fun clearUser(userId: String) {
        flows.remove(userId)
    }
}
