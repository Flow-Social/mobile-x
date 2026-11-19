package me.floow.uikit.chat.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize

@Stable
class MessageContextMenuState {
    var activeItem by mutableStateOf<ElevatedMessageData?>(null)
        private set

    val progress = Animatable(0f)

    val isVisible: Boolean
        get() = activeItem != null

    suspend fun show(item: ElevatedMessageData) {
        activeItem = item
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.68f,
                stiffness = 420f,
            )
        )
    }

    suspend fun dismiss() {
        if (activeItem == null) return
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 180,
                easing = FastOutLinearInEasing,
            )
        )
        activeItem = null
    }
}

data class ElevatedMessageData(
    val messageId: Long,
    val uiKey: String,
    val isMine: Boolean,
    val isVideoNote: Boolean,
    val layer: GraphicsLayer,
    val rootPosition: Offset,
    val size: IntSize,
    val videoCircleBoundsInRoot: Rect? = null,
    val startScale: Float = 1f,
    val tapOffset: Offset? = null,
)

@Composable
fun rememberMessageContextMenuState(): MessageContextMenuState {
    return remember { MessageContextMenuState() }
}

/**
 * Вычисляет вертикальный сдвиг (targetOffsetY), чтобы сообщение + меню
 * гарантированно поместились на экране с учетом safe area.
 * Для нативного видеокружка на iOS возвращается 0f, так как нативный плеер в LazyColumn не сдвигается.
 *
 * Алгоритм (Telegram iOS parity):
 * Шаг А: если меню влезает снизу с зазором 8.dp и отступом от низа -> shiftY = 0f.
 * Шаг Б: если снизу не влезает, но влезает сверху с зазором 8.dp и отступом от верха -> shiftY = 0f.
 * Шаг В: сдвигать только если не влезает ни снизу, ни сверху, ровно на минимальную дельту.
 */
fun calculateAutoShiftY(
    isVideoNote: Boolean,
    itemTop: Float,
    itemHeight: Float,
    screenHeight: Float,
    topSafeInset: Float,
    bottomSafeInset: Float,
    reactionsBarHeightPx: Float,
    menuHeightPx: Float,
    verticalGapPx: Float,
): Float {
    if (isVideoNote || itemHeight >= screenHeight * 0.45f) return 0f

    val itemBottom = itemTop + itemHeight
    val minTop = topSafeInset
    val maxBottom = screenHeight.minus(bottomSafeInset)

    val menuBottomIfBelow = itemBottom + verticalGapPx + menuHeightPx
    if (menuBottomIfBelow <= maxBottom) return 0f

    val menuTopIfAbove = itemTop.minus(verticalGapPx).minus(menuHeightPx)
    if (menuTopIfAbove >= minTop) return 0f

    val maxAllowedShiftUp = (itemTop.minus(minTop)).coerceAtLeast(0f)
    val shiftUpNeeded = (menuBottomIfBelow.minus(maxBottom)).coerceAtMost(maxAllowedShiftUp)
    val remainingOverflowBelow = menuBottomIfBelow.minus(shiftUpNeeded).minus(maxBottom)
    if (remainingOverflowBelow <= 0f) {
        return shiftUpNeeded.unaryMinus()
    }

    val maxAllowedShiftDown = (maxBottom.minus(itemBottom)).coerceAtLeast(0f)
    val shiftDownNeeded = (minTop.minus(menuTopIfAbove)).coerceAtMost(maxAllowedShiftDown)
    return shiftDownNeeded
}

fun calculateAutoShiftY(
    isVideoNote: Boolean,
    itemTop: Float,
    itemHeight: Int,
    screenHeight: Float,
    topSafeInset: Float,
    bottomSafeInset: Float,
    reactionsBarHeightPx: Float,
    menuHeightPx: Float,
    verticalGapPx: Float,
): Float = calculateAutoShiftY(
    isVideoNote = isVideoNote,
    itemTop = itemTop,
    itemHeight = itemHeight.toFloat(),
    screenHeight = screenHeight,
    topSafeInset = topSafeInset,
    bottomSafeInset = bottomSafeInset,
    reactionsBarHeightPx = reactionsBarHeightPx,
    menuHeightPx = menuHeightPx,
    verticalGapPx = verticalGapPx,
)
