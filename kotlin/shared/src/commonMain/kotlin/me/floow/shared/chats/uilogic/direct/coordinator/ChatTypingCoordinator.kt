package me.floow.shared.chats.uilogic.direct.coordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatTypingState
import me.floow.shared.chats.uilogic.direct.ChatRealtimeEvent
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository

class ChatTypingCoordinator(
	private val repository: ChatThreadRepository,
	private val scope: CoroutineScope,
) {
	companion object {
		const val TYPING_THROTTLE_MS = 3_000L
		const val TYPING_IDLE_STOP_MS = 1_200L
		const val MIN_INCOMING_TYPING_TTL_MS = 800L
	}

	private val _typingState = MutableStateFlow(ChatTypingState())
	val typingState: StateFlow<ChatTypingState> = _typingState.asStateFlow()

	private var outgoingTypingJob: Job? = null
	private var incomingTypingClearJob: Job? = null
	private var isOutgoingTypingActive: Boolean = false
	private var lastOutgoingTypingSentMark: TimeMark? = null

	fun scheduleOutgoingTyping(conversationId: Long, text: String) {
		outgoingTypingJob?.cancel()
		if (text.isBlank()) {
			stopOutgoingTyping(conversationId)
			return
		}
		val shouldSendTyping = !isOutgoingTypingActive || (
			lastOutgoingTypingSentMark?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE
		) >= TYPING_THROTTLE_MS
		if (shouldSendTyping) {
			isOutgoingTypingActive = true
			lastOutgoingTypingSentMark = TimeSource.Monotonic.markNow()
			scope.launch {
				repository.setTyping(conversationId, true)
			}
		}
		outgoingTypingJob = scope.launch {
			delay(TYPING_IDLE_STOP_MS)
			stopOutgoingTyping(conversationId)
		}
	}

	fun stopOutgoingTyping(conversationId: Long) {
		outgoingTypingJob?.cancel()
		if (!isOutgoingTypingActive) return
		isOutgoingTypingActive = false
		lastOutgoingTypingSentMark = null
		scope.launch {
			repository.setTyping(conversationId, false)
		}
	}

	fun handleIncomingTyping(
		event: ChatRealtimeEvent.TypingUpdated,
		currentHeader: ChatThreadHeaderModel?,
		onTypingChanged: (ChatTypingState) -> Unit,
	) {
		if (currentHeader == null) return
		if (event.actorUserId.isNotBlank() && event.actorUserId != currentHeader.peerUserId) {
			return
		}
		val nextState = ChatTypingState(
			isTyping = event.isTyping,
			displayName = event.displayName.ifBlank { currentHeader.title },
			action = event.action,
			typingTtlMs = event.typingTtlMs,
		)
		_typingState.value = nextState
		onTypingChanged(nextState)

		incomingTypingClearJob?.cancel()
		if (event.isTyping && event.typingTtlMs > 0L) {
			incomingTypingClearJob = scope.launch {
				delay(event.typingTtlMs.coerceAtLeast(MIN_INCOMING_TYPING_TTL_MS))
				val reset = ChatTypingState()
				_typingState.value = reset
				onTypingChanged(reset)
			}
		}
	}

	fun reset() {
		outgoingTypingJob?.cancel()
		outgoingTypingJob = null
		incomingTypingClearJob?.cancel()
		incomingTypingClearJob = null
		isOutgoingTypingActive = false
		lastOutgoingTypingSentMark = null
		_typingState.value = ChatTypingState()
	}

	fun dispose() {
		reset()
	}
}
