package me.floow.app.push

import android.net.Uri

object ChatPushDeepLink {
	fun buildUri(
		conversationId: Long,
		messageId: String?,
		interlocutorId: String?,
		interlocutorName: String?,
		interlocutorAvatarUrl: String? = null
	): Uri? {
		if (conversationId <= 0L) return null
		val builder = Uri.Builder()
			.scheme("me.floow.app")
			.authority("chat")
			.appendQueryParameter("conversation_id", conversationId.toString())
			.appendQueryParameter("source", "notification")
		messageId
			?.trim()
			?.takeIf(String::isNotEmpty)
			?.let { builder.appendQueryParameter("message_id", it) }
		interlocutorId
			?.trim()
			?.takeIf(String::isNotEmpty)
			?.let { builder.appendQueryParameter("interlocutor_id", it) }
		interlocutorName
			?.trim()
			?.takeIf(String::isNotEmpty)
			?.let { builder.appendQueryParameter("interlocutor_name", it) }
		interlocutorAvatarUrl
			?.trim()
			?.takeIf(String::isNotEmpty)
			?.let { builder.appendQueryParameter("interlocutor_avatar_url", it) }
		return builder.build()
	}

	fun buildRepliesUri(
		notificationSeq: String?,
		postId: String?,
		commentId: String?,
		replyToCommentId: String?,
		threadId: String?
	): Uri? {
		val hasTarget = !postId.isNullOrBlank() || !commentId.isNullOrBlank() || !threadId.isNullOrBlank()
		val hasSeq = notificationSeq?.trim()?.toLongOrNull()?.let { it > 0L } == true
		if (!hasTarget && !hasSeq) return null
		val builder = Uri.Builder()
			.scheme("me.floow.app")
			.authority("replies")
			.appendQueryParameter("source", "notification")
		notificationSeq
			?.trim()
			?.takeIf(String::isNotEmpty)
			?.let { builder.appendQueryParameter("notification_seq", it) }
		postId
			?.trim()
			?.takeIf(String::isNotEmpty)
			?.let { builder.appendQueryParameter("post_id", it) }
		commentId
			?.trim()
			?.takeIf(String::isNotEmpty)
			?.let { builder.appendQueryParameter("comment_id", it) }
		replyToCommentId
			?.trim()
			?.takeIf(String::isNotEmpty)
			?.let { builder.appendQueryParameter("reply_to_comment_id", it) }
		threadId
			?.trim()
			?.takeIf(String::isNotEmpty)
			?.let { builder.appendQueryParameter("thread_id", it) }
		return builder.build()
	}
}
