package me.floow.uikit.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

data class ChatTextSendSource(
    val layout: TextLayoutResult,
    val originInRoot: Offset,
    val viewportInRoot: Rect,
) {
    internal val glyphLeft = (0 until layout.lineCount).minOfOrNull { layout.getLineLeft(it) } ?: 0f
    internal val glyphRight = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) } ?: glyphLeft
}

@Stable
class ChatTextSendTransitions {
    private val pending = mutableStateMapOf<String, ChatTextSendFlight>()

    fun start(key: String, source: ChatTextSendSource) {
        pending[key] = ChatTextSendFlight(source)
    }

    internal operator fun get(key: String): ChatTextSendFlight? = pending[key]
    internal fun entries() = pending.toMap()
    internal fun latest() = pending.values.lastOrNull()

    internal fun finish(key: String, flight: ChatTextSendFlight) {
        if (pending[key] === flight) pending.remove(key)
    }
}

val LocalChatTextSendTransitions = staticCompositionLocalOf<ChatTextSendTransitions?> { null }

internal data class ChatTextSendTarget(val rectInRoot: Rect, val bubble: GraphicsLayer)

@Stable
internal class ChatTextSendFlight(val source: ChatTextSendSource) {
    val progress = Animatable(0f)
    var target by mutableStateOf<ChatTextSendTarget?>(null)
}

private val FlyToBubble = CubicBezierEasing(0.20f, 0.01f, 0.28f, 0.91f)
private val HorizontalFlight = CubicBezierEasing(0.20f, 0.06f, 0.32f, 1f)
private const val TextFlightDuration = 190

@Composable
fun ChatTextSendTransitionLayer(transitions: ChatTextSendTransitions, modifier: Modifier = Modifier) {
    val flights = transitions.entries()
    if (flights.isEmpty()) return
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val bubbleColor = ChatBubbleColors.Default.backgroundColor

    for ((id, flight) in flights) key(id, flight) {
        LaunchedEffect(flight) {
            try {
                val measured = withTimeoutOrNull(240L) {
                    snapshotFlow { flight.target }.filterNotNull().first()
                }
                if (measured != null) {
                    flight.progress.animateTo(1f, tween(TextFlightDuration, easing = LinearEasing))
                }
            } finally {
                transitions.finish(id, flight)
            }
        }
    }
    Canvas(modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.positionInRoot() }) {
        for (flight in flights.values) {
            val source = flight.source
            val from = source.originInRoot - rootOrigin
            val clip = source.viewportInRoot.translate(-rootOrigin)
            val target = flight.target
            val raw = flight.progress.value
            if (target == null || raw == 0f) {
                clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                    drawText(source.layout, topLeft = from)
                }
                continue
            }

            val x = HorizontalFlight.transform(raw)
            val y = FlyToBubble.transform(raw)
            val destination = target.rectInRoot.translate(-rootOrigin)
            val glyphStart = (from.x + source.glyphLeft).coerceIn(clip.left, clip.right)
            val glyphEnd = (from.x + source.glyphRight).coerceIn(glyphStart, clip.right)
            val visibleTop = maxOf(from.y, clip.top)
            val visibleBottom = minOf(from.y + source.layout.size.height, clip.bottom).coerceAtLeast(visibleTop)
            val fromShell = Rect(
                glyphStart - 10.dp.toPx(),
                visibleTop - 5.dp.toPx(),
                glyphEnd + 10.dp.toPx(),
                visibleBottom + 5.dp.toPx(),
            )
            val shell = Rect(
                mix(fromShell.left, destination.left, x),
                mix(fromShell.top, destination.top, y),
                mix(fromShell.right, destination.right, x),
                mix(fromShell.bottom, destination.bottom, y),
            )
            val actualBubbleAlpha = ((raw - 0.48f) / 0.46f).coerceIn(0f, 1f)
            val panelToBubble = (raw / 0.35f).coerceIn(0f, 1f)
            val sourceAlpha = 1f - actualBubbleAlpha
            if (sourceAlpha > 0f) {
                drawRoundRect(
                    color = bubbleColor,
                    topLeft = shell.topLeft,
                    size = shell.size,
                    cornerRadius = CornerRadius(mix(12.dp.toPx(), 20.dp.toPx(), y)),
                    alpha = panelToBubble,
                )
                val fontColor = source.layout.layoutInput.style.color
                val textColor = lerp(if (fontColor == Color.Unspecified) Color.Black else fontColor, Color.White, panelToBubble)
                val finalTextWidth = (glyphEnd - glyphStart).coerceAtMost((destination.width - 26.dp.toPx()).coerceAtLeast(0f))
                val textPoint = Offset(
                    mix(from.x, destination.center.x - finalTextWidth / 2f - source.glyphLeft, x),
                    mix(from.y, destination.top + 9.dp.toPx(), y),
                )
                clipRect(
                    mix(clip.left, 0f, x),
                    mix(clip.top, 0f, y),
                    mix(clip.right, size.width, x),
                    mix(clip.bottom, size.height, y),
                ) {
                    drawText(source.layout, color = textColor, topLeft = textPoint, alpha = sourceAlpha)
                }
            }
            if (actualBubbleAlpha > 0f && destination.width > 0 && destination.height > 0) {
                val layer = target.bubble
                layer.alpha = actualBubbleAlpha
                // Move the real bubble at its measured size. Scaling its layer also
                // stretches glyphs and makes the handoff look like a second resize.
                translate(shell.center.x - destination.width / 2f, shell.center.y - destination.height / 2f) {
                    drawLayer(layer)
                }
            }
        }
    }
}

private fun mix(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction
