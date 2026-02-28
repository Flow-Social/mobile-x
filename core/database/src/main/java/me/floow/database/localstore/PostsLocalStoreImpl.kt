package me.floow.database.localstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.floow.database.dao.PostsDao
import me.floow.database.dbo.PostEntity
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostContent
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class PostsLocalStoreImpl(
    private val postsDao: PostsDao
) : PostsLocalStore {
    override fun observePosts(userId: String): Flow<List<Post>> {
        return postsDao.observePosts(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPosts(userId: String): List<Post> {
        return postsDao.getPosts(userId).map { it.toDomain() }
    }

    override suspend fun getLastUpdatedAt(userId: String): Long? {
        return postsDao.getMaxUpdatedAt(userId)
    }

	override suspend fun replacePosts(userId: String, posts: List<Post>, updatedAt: Long) {
		val entities = posts.map { it.toEntity(userId, updatedAt) }
		val existing = postsDao.getPosts(userId)
		if (hasSameSnapshot(existing, entities)) {
			return
		}
		postsDao.replacePostsForUser(userId, entities)
	}

    override suspend fun clearUser(userId: String) {
        postsDao.deleteByUser(userId)
    }
}

private fun hasSameSnapshot(current: List<PostEntity>, incoming: List<PostEntity>): Boolean {
	if (current.size != incoming.size) return false
	if (current.isEmpty() && incoming.isEmpty()) return true
	val currentById = current.associateBy { it.postId }
	if (currentById.size != incoming.size) return false
	return incoming.all { next ->
		val existing = currentById[next.postId] ?: return@all false
		existing.sameContent(next)
	}
}

private fun PostEntity.sameContent(other: PostEntity): Boolean {
	return postId == other.postId &&
		userId == other.userId &&
		authorId == other.authorId &&
		authorName == other.authorName &&
		authorUsername == other.authorUsername &&
		authorAvatarUrl == other.authorAvatarUrl &&
		imageUrls == other.imageUrls &&
		imageVariants == other.imageVariants &&
		commentersPreview == other.commentersPreview &&
		description == other.description &&
		category == other.category &&
		createdAt == other.createdAt &&
		likesCount == other.likesCount &&
		commentsCount == other.commentsCount
}

@OptIn(RawValueObjectCreate::class)
private fun PostEntity.toDomain(): Post {
    return Post(
        id = postId,
        author = PostAuthor(
            id = authorId,
            name = authorName?.let { ProfileName.createRaw(it) },
            username = authorUsername?.let { ProfileUsername.createRaw(it) },
            avatarUrl = authorAvatarUrl
        ),
		content = PostContent(
			imageUrls = imageUrls,
			description = description,
            imageVariants = imageVariants
		),
		category = category,
        createdAt = createdAt,
        likesCount = likesCount,
        commentsCount = commentsCount,
        commentersPreview = commentersPreview
    )
}

private fun Post.toEntity(userId: String, updatedAt: Long): PostEntity {
    return PostEntity(
        postId = id,
        userId = userId,
        authorId = author.id,
        authorName = author.name?.value,
        authorUsername = author.username?.value,
        authorAvatarUrl = author.avatarUrl,
        imageUrls = content.imageUrls,
        imageVariants = content.imageVariants,
        commentersPreview = commentersPreview,
        description = content.description,
		category = category,
        createdAt = createdAt,
        likesCount = likesCount,
        commentsCount = commentsCount,
        updatedAt = updatedAt
    )
}
