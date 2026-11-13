package me.floow.uikit.chat.components

/**
 * Checks whether a given local file path physically exists and is not empty.
 */
expect fun platformChatFileExists(filePath: String): Boolean

/**
 * Resolves the image source model for rendering in chat bubbles (e.g. ChatAlbumBubble).
 *
 * Verifies that the local file physically exists via [fileExists].
 * If absent, falls back to [remoteUrl].
 */
fun resolveChatImageSource(
	localPath: String?,
	downloadedPath: String?,
	remoteUrl: String?,
	thumbnailUrl: String? = null,
	fileExists: (String) -> Boolean = ::platformChatFileExists,
): String? {
	val effectiveLocal = (downloadedPath ?: localPath)?.takeIf { it.isNotBlank() }
	val hasLocalFile = effectiveLocal != null && fileExists(effectiveLocal)
	return when {
		hasLocalFile -> effectiveLocal
		!remoteUrl.isNullOrBlank() -> remoteUrl
		else -> thumbnailUrl?.takeIf { it.isNotBlank() }
	}
}

/**
 * Legacy resolver preserved for verification / tests.
 */
fun resolveLegacyChatImageSource(
	localPath: String?,
	downloadedPath: String?,
	remoteUrl: String?,
	thumbnailUrl: String? = null,
): String? {
	val effectiveLocal = (downloadedPath ?: localPath)?.takeIf { it.isNotBlank() }
	val hasLocalFile = effectiveLocal != null
	return when {
		hasLocalFile -> effectiveLocal
		!remoteUrl.isNullOrBlank() -> remoteUrl
		else -> thumbnailUrl?.takeIf { it.isNotBlank() }
	}
}

/**
 * Resolves full-screen viewer source url.
 */
fun resolveViewerImageSource(
	localPath: String?,
	downloadedPath: String?,
	remoteUrl: String?,
	fileExists: (String) -> Boolean = ::platformChatFileExists,
): String {
	val effectiveLocal = (downloadedPath ?: localPath)?.takeIf { it.isNotBlank() }
	if (effectiveLocal != null && fileExists(effectiveLocal)) {
		return effectiveLocal
	}
	return remoteUrl.orEmpty()
}
