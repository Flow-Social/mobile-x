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
	override suspend fun getNextPost(): GetDataResponse<FeedPost> {
		logger.d("FeedRepositoryImpl.getNextPost", "Loading next post")
		
		if (buffer.isNotEmpty()) return GetDataResponse.Success(buffer.removeFirst())
		
		return when (val resp = feedApi.getFeed(limit = 10)) {
			is GetFeedResponse.Success -> {
				val mapped = resp.items.map { item ->
					val authorName = item.authorName?.let { ProfileName.createRaw(it) }
					val authorUsername = item.authorUsername?.let { ProfileUsername.createRaw(it) }
					
					FeedPost(
						post = Post(
							id = item.id,
							author = PostAuthor(
								id = item.authorId,
								name = authorName,
								username = authorUsername,
								avatarUrl = item.authorAvatarUrl
							),
							content = PostContent(
								imageUrls = item.imageUrls,
								description = item.description,
								imageVariants = item.imageVariants.map { variant ->
									PostImageVariant(
										lqUrl = variant.lqUrl,
										previewUrl = variant.previewUrl,
										fullUrl = variant.fullUrl,
										width = variant.width,
										height = variant.height
									)
								}
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
				
				if (mapped.isEmpty()) return GetDataResponse.Error(GetDataError.NoData)
				
				mapped.drop(1).forEach { buffer.addLast(it) }
				GetDataResponse.Success(mapped.first())
			}
			
			GetFeedResponse.Error -> GetDataResponse.Error(GetDataError.Other)
		}
	}
	
	override suspend fun recordSwipe(postId: String, isLiked: Boolean): UpdateDataResponse {
		logger.d("FeedRepositoryImpl.recordSwipe", "Recording swipe for post $postId, liked: $isLiked")
		
		return when (feedApi.recordSwipe(postId = postId, isLiked = isLiked)) {
			RecordSwipeResponse.Success -> UpdateDataResponse.Success
			RecordSwipeResponse.Error -> UpdateDataResponse.Failure(FailureError.Other)
		}
	}

	override suspend fun undoSwipe(postId: String): UpdateDataResponse {
		logger.d("FeedRepositoryImpl.undoSwipe", "Undo swipe for post $postId")

		return when (feedApi.undoSwipe(postId = postId)) {
			RecordSwipeResponse.Success -> UpdateDataResponse.Success
			RecordSwipeResponse.Error -> UpdateDataResponse.Failure(FailureError.Other)
		}
	}

	override fun clearSession() {
		buffer.clear()
	}
}
