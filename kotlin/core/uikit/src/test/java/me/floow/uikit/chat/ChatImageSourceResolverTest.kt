package me.floow.uikit.chat

import me.floow.uikit.chat.components.resolveChatImageSource
import me.floow.uikit.chat.components.resolveLegacyChatImageSource
import me.floow.uikit.chat.components.resolveViewerImageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatImageSourceResolverTest {

	private val existingFilePath = "/data/user/0/me.floow.app/cache/chat_media_outbox/editor_processed/img_existing.jpg"
	private val deadFilePath = "/data/user/0/me.floow.app/cache/chat_media_outbox/editor_processed/img_cleaned_up_after_1_hour.jpg"
	private val validRemoteUrl = "https://cdn.floow.me/uploads/chat_image_123.jpg"
	private val validThumbnailUrl = "https://cdn.floow.me/uploads/chat_image_123_thumb.jpg"

	private val fileExistsPredicate: (String) -> Boolean = { path ->
		path == existingFilePath
	}

	@Test
	fun proofOfBug_legacyCodeReturnsDeadLocalPathInsteadOfRemoteUrl() {
		// ДОКАЗАТЕЛЬСТВО БАГА:
		// В текущей реализации ChatAlbumBubble.kt проверяется только `effectiveLocalPath != null`.
		// Поэтому для исходящего сообщения с удаленным из кэша файлом legacy-код возвращает мёртвый путь!
		val legacyResult = resolveLegacyChatImageSource(
			localPath = deadFilePath,
			downloadedPath = null,
			remoteUrl = validRemoteUrl,
			thumbnailUrl = validThumbnailUrl,
		)

		// Legacy-код ошибочно выбирает deadFilePath, полностью игнорируя рабочий remoteUrl!
		assertEquals(deadFilePath, legacyResult)
		assertNotEquals(validRemoteUrl, legacyResult)
	}

	@Test
	fun scenario1_localPathPointsToNonExistentFile_withRemoteUrl_expectsRemoteUrl() {
		// Сценарий 1: localPath указывает на несуществующий файл + есть remoteUrl → ожидается remoteUrl
		val result = resolveChatImageSource(
			localPath = deadFilePath,
			downloadedPath = null,
			remoteUrl = validRemoteUrl,
			thumbnailUrl = validThumbnailUrl,
			fileExists = fileExistsPredicate,
		)

		assertEquals(validRemoteUrl, result)
	}

	@Test
	fun scenario2_localPathPointsToExistentFile_withRemoteUrl_expectsLocalPath() {
		// Сценарий 2: localPath указывает на существующий файл + есть remoteUrl → ожидается localPath
		val result = resolveChatImageSource(
			localPath = existingFilePath,
			downloadedPath = null,
			remoteUrl = validRemoteUrl,
			thumbnailUrl = validThumbnailUrl,
			fileExists = fileExistsPredicate,
		)

		assertEquals(existingFilePath, result)
	}

	@Test
	fun scenario3_incomingMessageWithoutLocalPath_expectsRemoteUrl() {
		// Входящие сообщения (где localPath == null): работают корректно в обоих случаях
		val result = resolveChatImageSource(
			localPath = null,
			downloadedPath = null,
			remoteUrl = validRemoteUrl,
			thumbnailUrl = validThumbnailUrl,
			fileExists = fileExistsPredicate,
		)

		assertEquals(validRemoteUrl, result)
	}

	@Test
	fun scenario4_viewerSource_deadLocalPathFallsBackToRemoteUrl() {
		// Для полноэкранного Viewer: при несуществующем локальном файле берётся remoteUrl
		val result = resolveViewerImageSource(
			localPath = deadFilePath,
			downloadedPath = null,
			remoteUrl = validRemoteUrl,
			fileExists = fileExistsPredicate,
		)

		assertEquals(validRemoteUrl, result)
	}
}
