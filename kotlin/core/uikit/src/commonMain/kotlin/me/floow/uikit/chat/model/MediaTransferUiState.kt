package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable

@Immutable
enum class MediaTransferPhase {
	Idle,
	Preparing,
	Uploading,
	Downloading,
	Caching,
	Failed,
}

@Immutable
enum class MediaTransferActionIcon {
	None,
	Loader,
	Upload,
	Download,
	Retry,
	Cancel,
	Play,
}

@Immutable
data class MediaTransferUiState(
	val phase: MediaTransferPhase,
	val progress: Float? = null,
	val actionIcon: MediaTransferActionIcon = MediaTransferActionIcon.None,
	val canRetry: Boolean = false,
	val canCancel: Boolean = false,
	val showDim: Boolean = true,
	val showBlur: Boolean = false,
	val visible: Boolean = true,
	val sizeText: String? = null,
) {
	companion object {
		val Hidden = MediaTransferUiState(
			phase = MediaTransferPhase.Idle,
			progress = null,
			actionIcon = MediaTransferActionIcon.None,
			canRetry = false,
			canCancel = false,
			showDim = false,
			showBlur = false,
			visible = false,
		)
	}
}
