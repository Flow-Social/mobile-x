package me.floow.shared.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.models.Post

class WasmPostsLocalStore : PostsLocalStore {
    private val postsByUser = MutableStateFlow<Map<String, List<Post>>>(emptyMap())
    private val updatedAtByUser = mutableMapOf<String, Long>()

    override fun observePosts(userId: String): Flow<List<Post>> {
        return postsByUser.map { it[userId].orEmpty() }
    }

    override suspend fun getPosts(userId: String): List<Post> {
        return postsByUser.value[userId].orEmpty()
    }

    override suspend fun getLastUpdatedAt(userId: String): Long? {
        return updatedAtByUser[userId]
    }

    override suspend fun replacePosts(userId: String, posts: List<Post>, updatedAt: Long) {
        updatedAtByUser[userId] = updatedAt
        postsByUser.update { current -> current + (userId to posts) }
    }

    override suspend fun clearUser(userId: String) {
        updatedAtByUser.remove(userId)
        postsByUser.update { current -> current - userId }
    }
}
