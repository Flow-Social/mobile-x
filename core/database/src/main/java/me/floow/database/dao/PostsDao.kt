package me.floow.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.floow.database.dbo.PostEntity

@Dao
interface PostsDao {
    @Query("SELECT * FROM posts WHERE userId = :userId ORDER BY createdAt DESC")
    fun observePosts(userId: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getPosts(userId: String): List<PostEntity>

    @Query("SELECT MAX(updatedAt) FROM posts WHERE userId = :userId")
    suspend fun getMaxUpdatedAt(userId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    @Query("DELETE FROM posts WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

    @Query("DELETE FROM posts WHERE userId = :userId AND postId NOT IN (:keepPostIds)")
    suspend fun deleteMissingByUser(userId: String, keepPostIds: List<String>)

    @Transaction
    suspend fun replacePostsForUser(userId: String, posts: List<PostEntity>) {
        val keepIds = posts.map { it.postId }.distinct()
        if (keepIds.isEmpty()) {
            deleteByUser(userId)
            return
        }
        deleteMissingByUser(userId, keepIds)
        insertAll(posts)
    }
}
