package me.floow.shared.chats.uilogic.common

data class PeerReadUpdateDecision(
	val nextPeerLastReadMessageId: Long,
	val shouldPersistPeerRead: Boolean,
	val persistedPeerReadMessageId: Long?,
)

fun resolvePeerReadUpdate(
	currentPeerLastReadMessageId: Long,
	actorUserId: String?,
	actorLastReadMessageId: Long?,
	peerUserId: String?,
): PeerReadUpdateDecision {
	val normalizedCurrent = currentPeerLastReadMessageId.coerceAtLeast(0L)
	val normalizedActorRead = actorLastReadMessageId?.coerceAtLeast(0L)
	val isPeerActor = !actorUserId.isNullOrBlank() && actorUserId == peerUserId
	val nextPeerRead = if (isPeerActor && normalizedActorRead != null) {
		maxOf(normalizedCurrent, normalizedActorRead)
	} else {
		normalizedCurrent
	}
	val shouldPersist = isPeerActor && normalizedActorRead != null && normalizedActorRead > 0L
	return PeerReadUpdateDecision(
		nextPeerLastReadMessageId = nextPeerRead,
		shouldPersistPeerRead = shouldPersist,
		persistedPeerReadMessageId = if (shouldPersist) normalizedActorRead else null,
	)
}
