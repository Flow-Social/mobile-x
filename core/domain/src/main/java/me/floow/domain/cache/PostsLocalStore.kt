package me.floow.domain.cache

import kotlinx.coroutines.flow.Flow
import me.floow.domain.models.Post

interface PostsLocalStore {
    fun observePosts(userId: String): Flow<List<Post>>
    suspend fun getPosts(userId: String): List<Post>
    suspend fun getLastUpdatedAt(userId: String): Long?
    suspend fun replacePosts(userId: String, posts: List<Post>, updatedAt: Long)
    suspend fun clearUser(userId: String)
}
