package me.floow.uikit.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

val RecordingLockPillWidth = 32.dp
val RecordingLockPillHeight = 39.dp
val RecordingLockPillLockedSize = 36.dp
val RecordingLockPillLowerOffset = 34.dp

@Composable
fun RecordingLockPill(
    isLocked: Boolean,
    lockProgress: Float,
    modifier: Modifier = Modifier,
    onPauseClick: (() -> Unit)? = null,
) {
    val lockedHop = remember { Animatable(0f) }
    LaunchedEffect(isLocked) {
        if (isLocked) {
            lockedHop.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 360
                    0f at 0 using FastOutSlowInEasing
                    -18f at 110 using FastOutSlowInEasing
                    7f at 235 using FastOutSlowInEasing
                    0f at 360 using FastOutSlowInEasing
                },
            )
        } else {
            lockedHop.snapTo(0f)
        }
    }
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "lock_pill_alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (isLocked) 1f else (0.92f + lockProgress * 0.08f),
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "lock_pill_scale",
    )
    val pillWidth by animateDpAsState(
        targetValue = if (isLocked) RecordingLockPillLockedSize else RecordingLockPillWidth,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "lock_pill_width",
    )
    val pillHeight by animateDpAsState(
        targetValue = if (isLocked) RecordingLockPillLockedSize else RecordingLockPillHeight,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "lock_pill_height",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isLocked) (RecordingLockPillLockedSize / 2) else 41.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "lock_pill_corner",
    )
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = modifier
            .size(width = pillWidth, height = pillHeight)
            .graphicsLayer {
                this.alpha = alpha
                translationY = if (isLocked) {
                    RecordingLockPillLowerOffset.toPx() + lockedHop.value
                } else {
                    (1f - lockProgress) * 12f
                }
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isLocked) 3.dp else 1.dp,
                shape = RoundedCornerShape(cornerRadius),
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .then(
                if (isLocked && onPauseClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPauseClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isLocked,
            transitionSpec = {
                fadeIn(animationSpec = tween(140)) togetherWith fadeOut(animationSpec = tween(100))
            },
            label = "lock_pill_icon_transition",
        ) { locked ->
            if (locked) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Пауза",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}
