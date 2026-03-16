package me.floow.app.push

import android.net.Uri

object ChatPushDeepLink {
	fun buildUri(
		conversationId: Long,
		messageId: String?,
		interlocutorId: String?,
		interlocutorName: String?
	): Uri? {
		if (conversationId <= 0L) return null
		val builder = Uri.Builder()
			.scheme("me.floow.app")
			.authority("chat")
			.appendQueryParameter("conversation_id", conversationId.toString())
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
		return builder.build()
	}
}
