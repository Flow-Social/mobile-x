package me.floow.domain.data.repos

interface DirectMessagesOutgoingRetryScheduler {
	fun enqueue(conversationId: Long)
}
