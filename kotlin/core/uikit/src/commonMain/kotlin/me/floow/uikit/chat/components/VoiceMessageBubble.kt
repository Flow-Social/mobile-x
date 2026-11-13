package me.floow.uikit.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.components.media.MediaTransferOverlay
import me.floow.uikit.chat.model.ChatMessageDeliveryStatus
import me.floow.uikit.chat.model.MediaTransferActionIcon
import me.floow.uikit.chat.model.MediaTransferPhase
import me.floow.uikit.chat.model.MediaTransferUiState
import me.floow.uikit.chat.model.VideoCircleUploadStatus
import me.floow.uikit.chat.model.VoiceMessage
import me.floow.uikit.chat.model.formatChatClockTime
import me.floow.uikit.theme.LocalTypography

import androidx.compose.ui.graphics.luminance

@Composable
fun VoiceMessageBubble(
    message: VoiceMessage,
    isPlaying: Boolean,
    currentPositionMs: Long,
    speed: Float,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleSpeed: () -> Unit,
    onRetryClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onErrorClick: () -> Unit = {},
    isBuffering: Boolean = false,
    uploadProgress: Float? = null,
    onCancelUpload: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isOut = message.isOutgoing
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bubbleBackgroundColor = if (isOut) Color(0xFF7A73F8) else if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3)
    val contentColor = if (isOut || isDark) Color.White else Color.Black

    val bubbleShape = RoundedCornerShape(20.dp)

    val totalDurationMs = message.durationMs.coerceAtLeast(1L)
    val displayPositionMs = if (isPlaying || currentPositionMs > 0L) {
        currentPositionMs
    } else {
        message.durationMs
    }

    val progress = (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)

    val isFailed = message.deliveryStatus == ChatMessageDeliveryStatus.FAILED ||
        message.uploadStatus == VideoCircleUploadStatus.Failed
    val isSending = message.deliveryStatus == ChatMessageDeliveryStatus.SENDING ||
        message.uploadStatus == VideoCircleUploadStatus.Uploading ||
        message.uploadStatus == VideoCircleUploadStatus.Pending

    Box(
        modifier = modifier
            .clip(bubbleShape)
            .background(bubbleBackgroundColor)
            .widthIn(min = 220.dp, max = 290.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Play/Pause / Upload Button
            if (isSending) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaTransferOverlay(
                        state = MediaTransferUiState(
                            phase = MediaTransferPhase.Uploading,
                            progress = uploadProgress,
                            actionIcon = MediaTransferActionIcon.Cancel,
                            canCancel = true,
                        ),
                        controlSize = 42.dp,
                        onCancel = { onCancelUpload?.invoke() ?: onDeleteClick() },
                    )
                }
            } else if (isFailed) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaTransferOverlay(
                        state = MediaTransferUiState(
                            phase = MediaTransferPhase.Failed,
                            actionIcon = MediaTransferActionIcon.Retry,
                            canRetry = true,
                        ),
                        controlSize = 42.dp,
                        onRetry = onRetryClick,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBuffering && isPlaying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(38.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    }
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Waveform Canvas
                WaveformBars(
                    waveform = message.waveform,
                    progress = progress,
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = contentColor.copy(alpha = 0.28f),
                    onSeek = onSeek,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom row: time, speed chip, clock & delivery status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = formatDuration(displayPositionMs),
                        style = LocalTypography.current.captionMedium.copy(
                            fontSize = 11.sp,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Speed toggle chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(contentColor.copy(alpha = 0.1f))
                            .clickable(onClick = onToggleSpeed)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = formatSpeed(speed),
                            style = LocalTypography.current.captionSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformBars(
    waveform: List<Int>,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveWaveform = if (waveform.isNotEmpty()) {
        waveform
    } else {
        List(40) { 20 }
    }

    Canvas(
        modifier = modifier
            .pointerInput(effectiveWaveform.size) {
                detectTapGestures { offset ->
                    val clickedProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(clickedProgress)
                }
            }
            .pointerInput(effectiveWaveform.size) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val p = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(p)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val p = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(p)
                    }
                )
            }
    ) {
        val count = effectiveWaveform.size
        val totalWidth = size.width
        val canvasHeight = size.height
        val barWidth = 2.5.dp.toPx()
        val spacing = if (count > 1) {
            ((totalWidth - barWidth * count) / (count - 1)).coerceAtLeast(1.dp.toPx())
        } else {
            0f
        }

        val activeIndexLimit = (progress * count).toInt().coerceIn(0, count)

        for (i in 0 until count) {
            val sample = effectiveWaveform[i].coerceIn(0, 100)
            val barHeight = ((sample / 100f) * canvasHeight).coerceAtLeast(3.dp.toPx())
            val x = i * (barWidth + spacing)
            val y = (canvasHeight - barHeight) / 2f

            val color = if (i <= activeIndexLimit) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

internal fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${if (seconds < 10) "0$seconds" else "$seconds"}"
}

private fun formatSpeed(speed: Float): String {
    return when {
        speed >= 1.9f -> "2X"
        speed >= 1.4f -> "1.5X"
        else -> "1X"
    }
}
