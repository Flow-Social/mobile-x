package me.floow.data.repos

import me.floow.domain.api.FeedApi
import me.floow.domain.api.models.GetFeedResponse
import me.floow.domain.api.models.RecordSwipeResponse
import me.floow.domain.data.FailureError
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.models.FeedPost
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostContent
import me.floow.domain.models.PostImageVariant
import me.floow.domain.utils.Logger
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class FeedRepositoryImpl(
	private val logger: Logger,
	private val feedApi: FeedApi,
) : FeedRepository {
	private val buffer = ArrayDeque<FeedPost>()

	@OptIn(RawValueObjectCreate::class)
	override suspend fun getNextPosts(limit: Int): GetDataResponse<List<FeedPost>> {
		val requestedLimit = limit.coerceAtLeast(1)
		logger.d("FeedRepositoryImpl.getNextPosts", "Loading next $requestedLimit posts")

		val result = mutableListOf<FeedPost>()
		while (buffer.isNotEmpty() && result.size < requestedLimit) {
			result.add(buffer.removeFirst())
		}
		if (result.size >= requestedLimit) {
			return GetDataResponse.Success(result)
		}

		val fetchLimit = maxOf(requestedLimit - result.size, 10)
		return when (val resp = feedApi.getFeed(limit = fetchLimit)) {
			is GetFeedResponse.Success -> {
				val mapped = resp.items.map(::mapToFeedPost)
				if (mapped.isEmpty() && result.isEmpty()) {
					return GetDataResponse.Error(GetDataError.NoData)
				}
				val needed = requestedLimit - result.size
				result.addAll(mapped.take(needed))
				buffer.addAll(mapped.drop(needed))
				GetDataResponse.Success(result)
			}
			is GetFeedResponse.Error -> {
				if (result.isNotEmpty()) {
					GetDataResponse.Success(result)
				} else {
					GetDataResponse.Error(GetDataError.Other)
				}
			}
		}
	}

	override suspend fun getNextPost(): GetDataResponse<FeedPost> {
		val posts = getNextPosts(1)
		return when (posts) {
			is GetDataResponse.Success -> {
				val post = posts.data.firstOrNull()
				if (post != null) GetDataResponse.Success(post) else GetDataResponse.Error(GetDataError.NoData)
			}
			is GetDataResponse.Error -> GetDataResponse.Error(posts.error)
		}
	}

	@OptIn(RawValueObjectCreate::class)
	private fun mapToFeedPost(item: me.floow.domain.api.models.FeedItem): FeedPost {
		val authorName = item.authorName?.let { ProfileName.createRaw(it) }
		val authorUsername = item.authorUsername?.let { ProfileUsername.createRaw(it) }

		return FeedPost(
			post = Post(
				id = item.id,
				author = PostAuthor(
					id = item.authorId,
					name = authorName,
					username = authorUsername,
					avatarUrl = item.authorAvatarUrl,
					avatarThumb = item.authorAvatarThumb
				),
				content = PostContent(
					imageUrls = item.imageUrls,
					description = item.description,
					imageVariants = item.imageVariants
				),
				category = item.category,
				createdAt = item.createdAt,
				likesCount = item.likesCount,
				commentsCount = item.commentsCount,
				commentersPreview = item.commentersPreview
			),
			reason = item.reason
		)
	}
	
	override suspend fun recordSwipe(postId: String, isLiked: Boolean): UpdateDataResponse {
		logger.d("FeedRepositoryImpl.recordSwipe", "Recording swipe for post $postId, liked: $isLiked")
		
		return when (feedApi.recordSwipe(postId = postId, isLiked = isLiked)) {
			RecordSwipeResponse.Success -> UpdateDataResponse.Success()
			RecordSwipeResponse.Error -> UpdateDataResponse.Failure(FailureError.Other)
		}
	}

	override suspend fun undoSwipe(postId: String): UpdateDataResponse {
		logger.d("FeedRepositoryImpl.undoSwipe", "Undo swipe for post $postId")

		return when (feedApi.undoSwipe(postId = postId)) {
			RecordSwipeResponse.Success -> UpdateDataResponse.Success()
			RecordSwipeResponse.Error -> UpdateDataResponse.Failure(FailureError.Other)
		}
	}

	override fun clearSession() {
		buffer.clear()
	}
}
