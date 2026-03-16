package me.floow.chats.uilogic.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.floow.domain.data.repos.ChatsRepository

internal class ChatTypingController(
	private val scope: CoroutineScope,
	private val chatsRepository: ChatsRepository,
	private val onIncomingTypingChanged: (isTyping: Boolean, displayName: String) -> Unit,
) {
	private var isOutgoingActive: Boolean = false
	private var lastOutgoingSentAtMs: Long = 0L
	private var incomingClearJob: Job? = null
	private var outgoingStopJob: Job? = null

	fun reset() {
		isOutgoingActive = false
		lastOutgoingSentAtMs = 0L
		incomingClearJob?.cancel()
		incomingClearJob = null
		outgoingStopJob?.cancel()
		outgoingStopJob = null
	}

	fun isOutgoingTypingActive(): Boolean = isOutgoingActive

	fun onInput(input: String, conversationId: Long) {
		if (input.isBlank()) {
			stopOutgoing(conversationId)
			return
		}
		startOutgoingIfNeeded(conversationId)
		scheduleOutgoingStop(conversationId)
	}

	fun applyIncomingTyping(isTyping: Boolean, ttlMs: Long, displayName: String) {
		if (!isTyping) {
			incomingClearJob?.cancel()
			onIncomingTypingChanged(false, displayName)
			return
		}
		onIncomingTypingChanged(true, displayName)
		incomingClearJob?.cancel()
		incomingClearJob = scope.launch {
			delay(ttlMs.coerceAtLeast(MIN_TYPING_TTL_MS))
			onIncomingTypingChanged(false, displayName)
		}
	}

	fun stopOutgoing(conversationId: Long) {
		outgoingStopJob?.cancel()
		if (!isOutgoingActive) return
		isOutgoingActive = false
		lastOutgoingSentAtMs = 0L
		scope.launch {
			chatsRepository.sendTyping(conversationId = conversationId, isTyping = false)
		}
	}

	private fun startOutgoingIfNeeded(conversationId: Long) {
		val now = System.currentTimeMillis()
		if (!isOutgoingActive) {
			isOutgoingActive = true
			lastOutgoingSentAtMs = now
			scope.launch {
				chatsRepository.sendTyping(conversationId = conversationId, isTyping = true)
			}
			return
		}
		if (now - lastOutgoingSentAtMs < TYPING_THROTTLE_MS) return
		lastOutgoingSentAtMs = now
		scope.launch {
			chatsRepository.sendTyping(conversationId = conversationId, isTyping = true)
		}
	}

	private fun scheduleOutgoingStop(conversationId: Long) {
		outgoingStopJob?.cancel()
		outgoingStopJob = scope.launch {
			delay(TYPING_IDLE_STOP_MS)
			stopOutgoing(conversationId)
		}
	}

	private companion object {
		const val TYPING_THROTTLE_MS = 3_000L
		const val TYPING_IDLE_STOP_MS = 1_200L
		const val MIN_TYPING_TTL_MS = 800L
	}
}
