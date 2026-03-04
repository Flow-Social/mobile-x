package me.floow.mock.data

import java.util.Base64
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.models.Comment
import me.floow.domain.models.CommentAuthor
import me.floow.domain.models.CommentReply
import me.floow.domain.models.CommentsPage
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

@OptIn(RawValueObjectCreate::class)
class MockCommentsRepository : CommentsRepository {
	private val idCounter = AtomicLong(1L)
	private val commentsByPostId: MutableMap<String, MutableList<Comment>> = mutableMapOf()
	private val lastReadSeqByPostId: MutableMap<String, Long> = mutableMapOf()

	init {
		val author = CommentAuthor(
			id = "user_anna",
			name = ProfileName.createRaw("Анна"),
			username = ProfileUsername.createRaw("anna"),
			avatarUrl = "https://picsum.photos/seed/anna/100/100"
		)
		val initial = mutableListOf<Comment>()
		val now = System.currentTimeMillis()
		repeat(10) { idx ->
			initial += Comment(
				id = idCounter.getAndIncrement().toString(),
				seq = idCounter.get() - 1L,
				postId = "post_1",
				author = author,
				text = "Комментарий #${idx + 1}",
				isRead = false,
				createdAt = now + idx * 1000L,
				updatedAt = now + idx * 1000L,
				replyTo = null
			)
		}
		commentsByPostId["post_1"] = initial
	}

	override suspend fun getComments(
		postId: String,
		cursor: String?,
		limit: Int,
		anchorCommentId: Long?,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> {
		val all = commentsByPostId[postId]?.sortedBy { it.createdAt }.orEmpty()
		val lastReadSeq = lastReadSeqByPostId[postId] ?: 0L
		val decorated = all.map { comment ->
			comment.copy(isRead = comment.author.id == "me" || comment.seq <= lastReadSeq)
		}

		val (slice, nextCursor) = if (anchorCommentId != null) {
			val targetIndex = decorated.indexOfFirst { it.id.toLongOrNull() == anchorCommentId }
			if (targetIndex >= 0) {
				val before = (anchorBefore ?: 20).coerceIn(0, 80)
				val after = (anchorAfter ?: 40).coerceIn(0, 80)
				val startIndex = (targetIndex - before).coerceAtLeast(0)
				val endExclusive = (targetIndex + after + 1).coerceAtMost(decorated.size)
				val window = decorated.subList(startIndex, endExclusive)
				val cursorValue = if (endExclusive < decorated.size) {
					val last = window.last()
					encodeCursor(last.createdAt, last.id.toLong())
				} else {
					null
				}
				window to cursorValue
			} else {
				val startIndex = if (cursor.isNullOrBlank()) {
					0
				} else {
					val (cursorTime, cursorId) = decodeCursor(cursor)
					decorated.indexOfFirst { it.createdAt > cursorTime || (it.createdAt == cursorTime && it.id.toLong() > cursorId) }
				}.coerceAtLeast(0)
				val page = decorated.drop(startIndex).take(limit)
				val cursorValue = if (page.size == limit) {
					val last = page.last()
					encodeCursor(last.createdAt, last.id.toLong())
				} else {
					null
				}
				page to cursorValue
			}
		} else {
			val startIndex = if (cursor.isNullOrBlank()) {
				0
			} else {
				val (cursorTime, cursorId) = decodeCursor(cursor)
				decorated.indexOfFirst { it.createdAt > cursorTime || (it.createdAt == cursorTime && it.id.toLong() > cursorId) }
			}.coerceAtLeast(0)
			val page = decorated.drop(startIndex).take(limit)
			val cursorValue = if (page.size == limit) {
				val last = page.last()
				encodeCursor(last.createdAt, last.id.toLong())
			} else {
				null
			}
			page to cursorValue
		}
		val unreadCandidates = decorated.filter { it.author.id != "me" && it.seq > lastReadSeq }

		return GetDataResponse.Success(
			CommentsPage(
				items = slice,
				nextCursor = nextCursor,
				unreadCount = unreadCandidates.size,
				lastReadSeq = lastReadSeq,
				firstUnreadSeq = unreadCandidates.minOfOrNull(Comment::seq),
				maxSeq = decorated.maxOfOrNull(Comment::seq) ?: 0L
			)
		)
	}

	override suspend fun getCommentsContext(
		postId: String,
		targetCommentId: Long,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> {
		return getComments(
			postId = postId,
			cursor = null,
			limit = 120,
			anchorCommentId = targetCommentId,
			anchorBefore = anchorBefore,
			anchorAfter = anchorAfter
		)
	}

	override suspend fun createComment(postId: String, text: String, replyToId: Long?): GetDataResponse<Comment> {
		val now = System.currentTimeMillis()
		val author = CommentAuthor(
			id = "me",
			name = ProfileName.createRaw("Me"),
			username = ProfileUsername.createRaw("me"),
			avatarUrl = "https://picsum.photos/seed/me/100/100"
		)
		val reply = replyToId?.let { replyId ->
			val replyComment = commentsByPostId[postId]?.firstOrNull { it.id.toLongOrNull() == replyId }
			replyComment?.let {
				CommentReply(
					id = it.id,
					text = it.text,
					authorId = it.author.id,
					authorName = it.author.name?.value
				)
			}
		}

		val nextId = idCounter.getAndIncrement()
		val comment = Comment(
			id = nextId.toString(),
			seq = nextId,
			postId = postId,
			author = author,
			text = text,
			isRead = true,
			createdAt = now,
			updatedAt = now,
			replyTo = reply
		)
		val list = commentsByPostId.getOrPut(postId) { mutableListOf() }
		list.add(comment)
		return GetDataResponse.Success(comment)
	}

	override suspend fun updateComment(commentId: String, text: String): UpdateDataResponse {
		commentsByPostId.values.forEach { list ->
			val index = list.indexOfFirst { it.id == commentId }
			if (index != -1) {
				val old = list[index]
				list[index] = old.copy(text = text, updatedAt = System.currentTimeMillis())
				return UpdateDataResponse.Success
			}
		}
		return UpdateDataResponse.Failure()
	}

	override suspend fun deleteComment(commentId: String): UpdateDataResponse {
		commentsByPostId.values.forEach { list ->
			val index = list.indexOfFirst { it.id == commentId }
			if (index != -1) {
				list.removeAt(index)
				return UpdateDataResponse.Success
			}
		}
		return UpdateDataResponse.Failure()
	}

	override suspend fun markCommentsReadUpTo(postId: String, readUpToSeq: Long): GetDataResponse<Long> {
		if (postId.isBlank() || readUpToSeq <= 0L) {
			return GetDataResponse.Error(error = me.floow.domain.data.GetDataError.Other)
		}
		val current = lastReadSeqByPostId[postId] ?: 0L
		val applied = maxOf(current, readUpToSeq)
		lastReadSeqByPostId[postId] = applied
		return GetDataResponse.Success(applied)
	}

	private fun encodeCursor(createdAt: Long, id: Long): String {
		val payload = "$createdAt:$id"
		return Base64.getEncoder().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
	}

	private fun decodeCursor(cursor: String): Pair<Long, Long> {
		val decoded = String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8)
		val parts = decoded.split(":")
		val time = parts.getOrNull(0)?.toLongOrNull() ?: 0L
		val id = parts.getOrNull(1)?.toLongOrNull() ?: 0L
		return time to id
	}
}
