package me.floow.chats.uilogic.chat

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.floow.domain.utils.Logger
import me.floow.shared.chats.uilogic.direct.RecordedClip
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class PreparedVideoCircleUpload(
	val file: File,
	val durationMs: Long,
	val width: Int,
	val height: Int,
	val deleteAfterUpload: Boolean,
)

@OptIn(UnstableApi::class)
class AndroidVideoCircleCompressor(
	private val context: Context,
	private val logger: Logger,
) {
	private val mainHandler = Handler(Looper.getMainLooper())

	suspend fun prepare(clip: RecordedClip): PreparedVideoCircleUpload {
		val input = File(clip.path)
		require(input.exists() && input.length() > 0L) { "video circle file is missing" }
		if (input.length() <= SkipCompressionBytes) {
			return clip.toPreparedUpload(input, deleteAfterUpload = false)
		}

		val output = createOutputFile(input)
		return try {
			transform(input = input, output = output, durationMs = clip.durationMs)
			val prepared = readPreparedUpload(output, fallback = clip, deleteAfterUpload = true)
			when {
				prepared.file.length() <= MaxUploadBytes -> prepared
				input.length() <= MaxUploadBytes -> {
					output.delete()
					clip.toPreparedUpload(input, deleteAfterUpload = false)
				}
				else -> {
					output.delete()
					error("video circle is too large after compression")
				}
			}
		} catch (throwable: Throwable) {
			output.delete()
			if (input.length() <= MaxUploadBytes) {
				logger.d("AndroidVideoCircleCompressor", "Compression failed, using original: ${throwable.message}")
				clip.toPreparedUpload(input, deleteAfterUpload = false)
			} else {
				throw throwable
			}
		}
	}

	private suspend fun transform(input: File, output: File, durationMs: Long) {
		suspendCancellableCoroutine<Unit> { continuation ->
			var transformer: Transformer? = null
			mainHandler.post {
				if (!continuation.isActive) return@post
				try {
					val videoBitrate = chooseVideoBitrate(durationMs)
					val encoderFactory = DefaultEncoderFactory.Builder(context)
						.setRequestedVideoEncoderSettings(
							VideoEncoderSettings.Builder()
								.setBitrate(videoBitrate)
								.setiFrameIntervalSeconds(1f)
								.build()
						)
						.setRequestedAudioEncoderSettings(
							AudioEncoderSettings.Builder()
								.setBitrate(AudioBitrate)
								.build()
						)
						.build()
					val listener = object : Transformer.Listener {
						override fun onCompleted(composition: Composition, exportResult: ExportResult) {
							if (continuation.isActive) continuation.resume(Unit)
						}

						override fun onError(
							composition: Composition,
							exportResult: ExportResult,
							exportException: ExportException,
						) {
							if (continuation.isActive) continuation.resumeWithException(exportException)
						}
					}
					val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
						.setFrameRate(OutputFrameRate)
						.setEffects(
							Effects(
								emptyList(),
								listOf<Effect>(Presentation.createForHeight(OutputHeight)),
							)
						)
						.build()
					transformer = Transformer.Builder(context)
						.setVideoMimeType(MimeTypes.VIDEO_H264)
						.setAudioMimeType(MimeTypes.AUDIO_AAC)
						.setEncoderFactory(encoderFactory)
						.addListener(listener)
						.build()
					transformer.start(editedMediaItem, output.absolutePath)
				} catch (throwable: Throwable) {
					if (continuation.isActive) continuation.resumeWithException(throwable)
				}
			}
			continuation.invokeOnCancellation {
				mainHandler.post {
					transformer?.cancel()
					output.delete()
				}
			}
		}
	}

	private suspend fun readPreparedUpload(
		output: File,
		fallback: RecordedClip,
		deleteAfterUpload: Boolean,
	): PreparedVideoCircleUpload = withContext(Dispatchers.IO) {
		val metadata = readMetadata(output)
		PreparedVideoCircleUpload(
			file = output,
			durationMs = metadata.durationMs.takeIf { it > 0L } ?: fallback.durationMs,
			width = metadata.width.takeIf { it > 0 } ?: fallback.width,
			height = metadata.height.takeIf { it > 0 } ?: fallback.height,
			deleteAfterUpload = deleteAfterUpload,
		)
	}

	private fun RecordedClip.toPreparedUpload(file: File, deleteAfterUpload: Boolean): PreparedVideoCircleUpload {
		return PreparedVideoCircleUpload(
			file = file,
			durationMs = durationMs,
			width = width,
			height = height,
			deleteAfterUpload = deleteAfterUpload,
		)
	}

	private fun createOutputFile(input: File): File {
		val dir = File(context.cacheDir, "video_circle_uploads")
		dir.mkdirs()
		return File(dir, "${input.nameWithoutExtension}_compressed_${System.currentTimeMillis()}.mp4")
	}

	private fun chooseVideoBitrate(durationMs: Long): Int {
		val durationSeconds = (durationMs.coerceAtLeast(1_000L) / 1_000f)
		val totalBitrate = ((TargetUploadBytes * 8f) / durationSeconds).roundToInt()
		return (totalBitrate - AudioBitrate).coerceIn(MinVideoBitrate, MaxVideoBitrate)
	}

	private fun readMetadata(file: File): VideoMetadata {
		val retriever = MediaMetadataRetriever()
		return try {
			retriever.setDataSource(file.absolutePath)
			VideoMetadata(
				durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
					?.toLongOrNull()
					?: 0L,
				width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
					?.toIntOrNull()
					?: 0,
				height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
					?.toIntOrNull()
					?: 0,
			)
		} finally {
			retriever.release()
		}
	}

	private data class VideoMetadata(
		val durationMs: Long,
		val width: Int,
		val height: Int,
	)

	private companion object {
		const val MaxUploadBytes = 5L * 1024L * 1024L
		const val TargetUploadBytes = 1_800_000L
		const val SkipCompressionBytes = 1_200_000L
		const val OutputHeight = 360
		const val OutputFrameRate = 24
		const val AudioBitrate = 48_000
		const val MinVideoBitrate = 180_000
		const val MaxVideoBitrate = 650_000
	}
}
