package me.floow.uikit.chat.states

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ChatSkeletonBubbleShape = RoundedCornerShape(20.dp)
private val ChatSkeletonRows = listOf(
	ChatSkeletonRowSpec(outgoing = false, width = 220.dp, height = 54.dp, topSpacing = 0.dp),
	ChatSkeletonRowSpec(outgoing = true, width = 190.dp, height = 38.dp, topSpacing = 5.dp),
	ChatSkeletonRowSpec(outgoing = true, width = 286.dp, height = 72.dp, topSpacing = 4.dp),
	ChatSkeletonRowSpec(outgoing = false, width = 168.dp, height = 38.dp, topSpacing = 10.dp),
	ChatSkeletonRowSpec(outgoing = false, width = 260.dp, height = 72.dp, topSpacing = 4.dp),
	ChatSkeletonRowSpec(outgoing = true, width = 148.dp, height = 38.dp, topSpacing = 8.dp),
	ChatSkeletonRowSpec(outgoing = false, width = 238.dp, height = 54.dp, topSpacing = 10.dp),
	ChatSkeletonRowSpec(outgoing = true, width = 224.dp, height = 54.dp, topSpacing = 5.dp),
)

@Composable
fun ChatMessagesSkeleton(
	modifier: Modifier = Modifier,
	showIncomingAvatarSpace: Boolean = false,
) {
	BoxWithConstraints(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surfaceContainer),
	) {
		val density = LocalDensity.current
		val shimmerBrush = rememberChatSkeletonShimmerBrush(
			widthPx = with(density) { maxWidth.toPx() },
			heightPx = with(density) { maxHeight.toPx() },
		)
		val maxBubbleWidth = (maxWidth - 28.dp)
			.coerceAtMost(324.dp)
			.coerceAtLeast(72.dp)

		Column(
			verticalArrangement = Arrangement.Bottom,
			modifier = Modifier
				.fillMaxSize()
				.padding(top = 20.dp, bottom = 16.dp),
		) {
			ChatSkeletonRows.forEach { row ->
				Spacer(Modifier.height(row.topSpacing))
				ChatSkeletonRow(
					spec = row,
					shimmerBrush = shimmerBrush,
					maxBubbleWidth = maxBubbleWidth,
					showIncomingAvatarSpace = showIncomingAvatarSpace,
				)
			}
		}
	}
}

@Composable
private fun rememberChatSkeletonShimmerBrush(
	widthPx: Float,
	heightPx: Float,
): Brush {
	val transition = rememberInfiniteTransition(label = "chat_messages_skeleton_shimmer")
	val progress by transition.animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 1250, easing = LinearEasing),
			repeatMode = RepeatMode.Restart,
		),
		label = "chat_messages_skeleton_shimmer_progress",
	)
	val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
	val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
	val travel = remember(widthPx, heightPx) { widthPx + heightPx }
	val start = -travel + progress * travel * 2f

	return Brush.linearGradient(
		colors = listOf(baseColor, shimmerColor, baseColor),
		start = Offset(start, 0f),
		end = Offset(start + travel, heightPx),
	)
}

@Composable
private fun ChatSkeletonRow(
	spec: ChatSkeletonRowSpec,
	shimmerBrush: Brush,
	maxBubbleWidth: Dp,
	showIncomingAvatarSpace: Boolean,
) {
	Row(
		horizontalArrangement = if (spec.outgoing) Arrangement.End else Arrangement.Start,
		verticalAlignment = Alignment.Bottom,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp),
	) {
		if (!spec.outgoing && showIncomingAvatarSpace) {
			Spacer(Modifier.size(36.dp))
			Spacer(Modifier.width(8.dp))
		}

		Box(
			modifier = Modifier
				.width(spec.width.coerceAtMost(maxBubbleWidth))
				.height(spec.height)
				.clip(ChatSkeletonBubbleShape)
				.background(shimmerBrush),
		)
	}
}

private data class ChatSkeletonRowSpec(
	val outgoing: Boolean,
	val width: Dp,
	val height: Dp,
	val topSpacing: Dp,
)
