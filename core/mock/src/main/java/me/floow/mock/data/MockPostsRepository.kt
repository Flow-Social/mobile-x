package me.floow.mock.data

import kotlinx.coroutines.delay
import me.floow.domain.api.models.CreatePostData
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostCategories
import me.floow.domain.models.PostContent
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class MockPostsRepository : PostsRepository {
	private var idCounter = 1

	@OptIn(RawValueObjectCreate::class)
	private val postsByUserId: MutableMap<String, MutableList<Post>> =
		mutableMapOf(
			"me" to mutableListOf(
				Post(
					id = "me_${idCounter++}",
					author = PostAuthor(
						id = "me",
						name = ProfileName.createRaw("Me"),
						username = ProfileUsername.createRaw("me"),
						avatarUrl = "https://picsum.photos/seed/me/100/100"
					),
					content = PostContent(
						imageUrls = listOf("https://picsum.photos/seed/me_post1/600/800"),
						description = "Мой первый пост в Flow"
					),
					category = PostCategories.LIFESTYLE,
					createdAt = System.currentTimeMillis()
				)
			),
			"user_anna" to mutableListOf(
				Post(
					id = "anna_${idCounter++}",
					author = PostAuthor(
						id = "user_anna",
						name = ProfileName.createRaw("Анна"),
						username = ProfileUsername.createRaw("anna"),
						avatarUrl = "https://picsum.photos/seed/anna/100/100"
					),
					content = PostContent(
						imageUrls = listOf("https://picsum.photos/seed/anna_post1/600/800"),
						description = "Привет! Это мой профиль и мои посты"
					),
					category = PostCategories.NATURE,
					createdAt = System.currentTimeMillis()
				)
			)
		)

	override suspend fun getUserPosts(
		userId: String,
		forceNetwork: Boolean,
		limit: Int,
		offset: Int
	): GetDataResponse<List<Post>> {
		delay(250)
		val allPosts = postsByUserId[userId]?.toList().orEmpty()
		val from = offset.coerceAtLeast(0).coerceAtMost(allPosts.size)
		val to = (from + limit.coerceAtLeast(1)).coerceAtMost(allPosts.size)
		return GetDataResponse.Success(allPosts.subList(from, to))
	}

	override suspend fun getPostById(postId: String): GetDataResponse<Post> {
		delay(150)
		for ((_, posts) in postsByUserId) {
			val found = posts.firstOrNull { it.id == postId }
			if (found != null) {
				return GetDataResponse.Success(found)
			}
		}
		return GetDataResponse.Error(GetDataError.Other)
	}

	@OptIn(RawValueObjectCreate::class)
	override suspend fun createPost(data: CreatePostData): UpdateDataResponse {
		delay(250)
		val authorId = "me"
		val post = Post(
			id = "me_${idCounter++}",
			author = PostAuthor(
				id = authorId,
				name = ProfileName.createRaw("Me"),
				username = ProfileUsername.createRaw("me"),
				avatarUrl = "https://picsum.photos/seed/me/100/100"
			),
			content = PostContent(
				imageUrls = data.imageUrls,
				description = data.description
			),
			category = data.category,
			createdAt = System.currentTimeMillis()
		)

		val list = postsByUserId.getOrPut(authorId) { mutableListOf() }
		list.add(0, post)
		return UpdateDataResponse.Success
	}

	override suspend fun deletePost(postId: String): UpdateDataResponse {
		delay(250)
		for ((_, posts) in postsByUserId) {
			if (posts.removeIf { it.id == postId }) {
				return UpdateDataResponse.Success
			}
		}
		return UpdateDataResponse.Failure()
	}

	override suspend fun updatePost(
		postId: String,
		description: String?,
		imageUrls: List<String>?
	): UpdateDataResponse {
		delay(250)
		for ((_, posts) in postsByUserId) {
			val index = posts.indexOfFirst { it.id == postId }
			if (index != -1) {
				val oldPost = posts[index]
				val newContent = oldPost.content.copy(
					description = description ?: oldPost.content.description,
					imageUrls = imageUrls ?: oldPost.content.imageUrls
				)
				posts[index] = oldPost.copy(content = newContent)
				return UpdateDataResponse.Success
			}
		}
		return UpdateDataResponse.Failure()
	}
}
