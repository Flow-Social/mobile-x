package me.floow.domain.cache

object PostsCacheKeys {
	const val SELF_USER_ID: String = "me"
	const val FEED_STACK_USER_ID: String = "__feed_stack__"
	private const val FEED_STACK_PREFIX: String = "__feed_stack__"
	const val FEED_SYNC_QUEUE_USER_ID: String = "__feed_sync_queue__"

	fun feedStackUserId(userId: String): String {
		return "${FEED_STACK_PREFIX}:${userId}"
	}

	fun feedSyncQueueUserId(userId: String): String {
		return "${FEED_SYNC_QUEUE_USER_ID}:$userId"
	}
}
