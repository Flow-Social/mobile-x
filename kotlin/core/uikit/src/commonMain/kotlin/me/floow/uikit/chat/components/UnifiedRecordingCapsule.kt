package me.floow.uikit.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.model.ChatMediaRecordingState
import me.floow.uikit.chat.model.ChatMediaRecordingType
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.chat_selection_delete_icon

@Composable
fun UnifiedRecordingCapsule(
    state: ChatMediaRecordingState,
    onCancelClick: () -> Unit,
    onStopClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 4.dp),
    ) {
        if (state.isLocked) {
            // --- Залоченный режим (Locked State) ---
            // Слева кнопка корзины для удаления
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancelClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chat_selection_delete_icon),
                    contentDescription = "Удалить запись",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Точка индикатора REC / PAUSE
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .graphicsLayer { alpha = if (state.isPaused) 1f else dotAlpha }
                    .clip(CircleShape)
                    .background(if (state.isPaused) MaterialTheme.colorScheme.primary else Color.Red)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Таймер
            Text(
                text = state.formattedDuration,
                style = LocalTypography.current.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Живой эквалайзер на всю ширину
            RecordingWaveform(
                audioLevel = if (state.isPaused) 0.18f else state.audioLevel,
                barCount = if (state.isLocked) 28 else 18,
                modifier = Modifier.weight(1f),
            )
        } else {
            // --- Обычный режим удержания (Holding / Dragging) ---
            // Мигающая точка REC
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .clip(CircleShape)
                    .background(Color.Red)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Таймер
            Text(
                text = state.formattedDuration,
                style = LocalTypography.current.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Волна (сужается при оттягивании влево)
            val waveAlpha by animateFloatAsState(
                targetValue = (1f - state.cancelProgress * 1.5f).coerceIn(0f, 1f),
                label = "wave_alpha"
            )
            if (waveAlpha > 0.05f) {
                RecordingWaveform(
                    audioLevel = state.audioLevel,
                    barCount = 14,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = waveAlpha },
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Блок отмены (свайп влево + анимированная мусорка)
            val trashScale by animateFloatAsState(
                targetValue = if (state.cancelProgress > 0.1f) {
                    0.8f + state.cancelProgress * 0.4f
                } else 0f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "trash_scale"
            )
            val isNearCancel = state.cancelProgress >= 0.85f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset(x = (state.dragOffsetX * 0.35f).dp.coerceAtMost(0.dp))
            ) {
                if (trashScale > 0.05f) {
                    Icon(
                        painter = painterResource(Res.drawable.chat_selection_delete_icon),
                        contentDescription = "Отмена",
                        tint = if (isNearCancel) Color(0xFFEF4444) else Color.Gray,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = trashScale
                                scaleY = trashScale
                            }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = "‹ смахивание для отмены",
                    style = LocalTypography.current.labelMedium.copy(
                        fontSize = 12.sp,
                        color = if (isNearCancel) Color(0xFFEF4444) else Color.Gray
                    ),
                    modifier = Modifier.graphicsLayer {
                        alpha = (1f - state.cancelProgress * 0.6f).coerceIn(0.2f, 1f)
                    }
                )
            }
        }
    }
}

@Composable
private fun RecordingWaveform(
    audioLevel: Float,
    barCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.height(22.dp),
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        for (i in 0 until barCount) {
            val variance = ((i % 5) + 2) / 6f
            val fraction = (0.18f + (audioLevel * variance)).coerceIn(0.12f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .height((fraction * 22).dp.coerceAtLeast(3.dp))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(primaryColor.copy(alpha = 0.82f))
            )
        }
    }
}
