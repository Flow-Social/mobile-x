package me.floow.database.dbo

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import me.floow.domain.models.PostImageVariant

@Entity(
    tableName = "posts",
    primaryKeys = ["postId", "userId"],
    indices = [Index(value = ["userId"])]
)
data class PostEntity(
    val postId: String,
    val userId: String,
    val authorId: String,
    val authorName: String?,
    val authorUsername: String?,
    val authorAvatarUrl: String?,
    @ColumnInfo(name = "image_urls")
    val imageUrls: List<String>,
    @ColumnInfo(name = "image_variants")
    val imageVariants: List<PostImageVariant> = emptyList(),
    @ColumnInfo(name = "commenters_preview")
    val commentersPreview: List<String>,
    val description: String?,
    val category: String,
    val createdAt: Long,
    val likesCount: Int,
    val commentsCount: Int,
    val updatedAt: Long
)
