package me.floow.shared.chats.uilogic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.floow.shared.chats.model.ConversationActivityState

class ConversationActivityStore {
	private val _localActivities = MutableStateFlow<Map<Long, ConversationActivityState>>(emptyMap())
	val localActivities: StateFlow<Map<Long, ConversationActivityState>> = _localActivities.asStateFlow()
	private val draftTexts = mutableMapOf<Long, String>()
	private val draftTextsByKey = mutableMapOf<String, String>()

	fun setLocalActivity(conversationId: Long, activityState: ConversationActivityState) {
		if (conversationId <= 0L) return
		when (activityState) {
			is ConversationActivityState.Draft,
			is ConversationActivityState.RecordingVideo -> _localActivities.update { current ->
				current + (conversationId to activityState)
			}
			ConversationActivityState.None,
			is ConversationActivityState.Sending,
			is ConversationActivityState.Typing -> clearLocalActivity(conversationId)
		}
	}

	fun clearLocalActivity(conversationId: Long) {
		if (conversationId <= 0L) return
		_localActivities.update { current ->
			if (conversationId !in current) current else current - conversationId
		}
	}

	fun setDraftText(conversationId: Long, text: String) {
		if (conversationId <= 0L) return
		val normalized = text.takeIf(String::isNotBlank)
		if (normalized == null) {
			draftTexts.remove(conversationId)
		} else {
			draftTexts[conversationId] = normalized
		}
	}

	fun setDraftTextByKey(key: String, text: String) {
		val trimmedKey = key.trim().takeIf(String::isNotEmpty) ?: return
		val normalized = text.takeIf(String::isNotBlank)
		if (normalized == null) {
			draftTextsByKey.remove(trimmedKey)
		} else {
			draftTextsByKey[trimmedKey] = normalized
		}
	}

	fun getDraftText(conversationId: Long?): String {
		val resolvedConversationId = conversationId?.takeIf { it > 0L } ?: return ""
		return draftTexts[resolvedConversationId].orEmpty()
	}

	fun getDraftTextByKey(key: String?): String {
		val trimmedKey = key?.trim()?.takeIf(String::isNotEmpty) ?: return ""
		return draftTextsByKey[trimmedKey].orEmpty()
	}
}
