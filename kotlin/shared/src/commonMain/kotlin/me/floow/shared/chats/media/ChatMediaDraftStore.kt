package me.floow.shared.chats.media

import me.floow.shared.chats.uilogic.direct.RecordedVoiceClip
import me.floow.uikit.chat.model.ChatDraftImage
import me.floow.uikit.chat.model.VoiceDraftUiState

data class ChatMediaDraft(
	val images: List<ChatDraftImage> = emptyList(),
	val voiceDraft: VoiceDraftUiState? = null,
	val voiceClip: RecordedVoiceClip? = null,
) {
	val isEmpty: Boolean
		get() = images.isEmpty() && voiceDraft == null && voiceClip == null
}

object ChatMediaDraftStore {
	private val drafts = mutableMapOf<String, ChatMediaDraft>()

	fun getDraft(key: String): ChatMediaDraft {
		return drafts[key] ?: ChatMediaDraft()
	}

	fun updateImages(key: String, images: List<ChatDraftImage>) {
		if (key.isBlank()) return
		val current = getDraft(key)
		val updated = current.copy(images = images)
		if (updated.isEmpty) {
			drafts.remove(key)
		} else {
			drafts[key] = updated
		}
	}

	fun updateVoice(key: String, voiceDraft: VoiceDraftUiState?, voiceClip: RecordedVoiceClip?) {
		if (key.isBlank()) return
		val current = getDraft(key)
		val updated = current.copy(voiceDraft = voiceDraft, voiceClip = voiceClip)
		if (updated.isEmpty) {
			drafts.remove(key)
		} else {
			drafts[key] = updated
		}
	}

	fun clear(key: String) {
		drafts.remove(key)
	}
}
