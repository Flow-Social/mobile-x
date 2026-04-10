package me.floow.uikit.components.swipe

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * State manager for swiping logic.
 * Decouples animation and gesture logic from the UI.
 */
@Stable
class SwipeCardState(
    internal val config: SwipeConfig,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    val rotation = Animatable(0f)
    val scale = Animatable(1f)
    val overlayAlpha = Animatable(0f)
    
    var isDragging by mutableStateOf(false)
        internal set

    var hapticThresholdReached by mutableStateOf(false)
        internal set

    var containerSize by mutableStateOf(IntSize.Zero)

    fun onDragStart() {
        isDragging = true
        hapticThresholdReached = false
    }

    fun onDrag(dragAmount: Offset) {
        scope.launch {
            val resistanceX = config.resistanceHorizontal
            val resistanceY = if (dragAmount.y > 0) {
                config.resistanceVerticalDown 
            } else {
                config.resistanceVerticalUp
            }

            val newX = offset.value.x + (dragAmount.x * resistanceX)
            val newY = offset.value.y + (dragAmount.y * resistanceY)
            
            offset.snapTo(Offset(newX, newY))
            
            val maxWidth = containerSize.width.toFloat().takeIf { it > 0 } ?: 1000f
            rotation.snapTo((newX / maxWidth) * config.maxRotation)
        }
    }

    fun checkThreshold(): Boolean {
        val maxWidth = containerSize.width.toFloat().takeIf { it > 0 } ?: 1000f
        val hThreshold = maxWidth * config.swipeThreshold
        val vThresholdUp = containerSize.height * config.swipeThresholdUp
        val vThresholdDown = containerSize.height * config.swipeThresholdDown
        
        val x = offset.value.x
        val y = offset.value.y

        val crossed = abs(x) > hThreshold || 
                      (y < 0 && abs(y) > vThresholdUp) || 
                      (y > 0 && y > vThresholdDown)
        
        // Only update state if changed to avoid unnecessary recompositions
        if (crossed != hapticThresholdReached) {
            hapticThresholdReached = crossed
        }
        return crossed
    }

    fun onDragEnd(
        onSwipe: (SwipeDirection) -> Unit,
        canDismiss: (SwipeDirection) -> Boolean = { true },
        onSwipeWithoutDismiss: (SwipeDirection) -> Unit = {}
    ): Boolean {
        isDragging = false
        val x = offset.value.x
        val y = offset.value.y
        
        // We can reuse checkThreshold logic but we need direction too
        val maxWidth = containerSize.width.toFloat().takeIf { it > 0 } ?: 1000f
        val hThreshold = maxWidth * config.swipeThreshold
        val vThresholdUp = containerSize.height * config.swipeThresholdUp
        val vThresholdDown = containerSize.height * config.swipeThresholdDown
        
        val isHorizontal = abs(x) > abs(y)
        
        val swipeDir = if (isHorizontal) {
            when {
                x > hThreshold -> SwipeDirection.Right
                x < -hThreshold -> SwipeDirection.Left
                else -> null
            }
        } else {
            when {
                y > vThresholdDown -> SwipeDirection.Down
                y < -vThresholdUp -> SwipeDirection.Up
                else -> null
            }
        }
        
        if (swipeDir == null) {
            snapBack()
            return false
        }

        if (canDismiss(swipeDir)) {
            onSwipe(swipeDir)
            return true
        }

        onSwipeWithoutDismiss(swipeDir)
        snapBack()
        return false
    }

    fun onDragCancel() {
        isDragging = false
        snapBack()
    }

    private fun snapBack() {
        scope.launch {
            launch {
                offset.animateTo(
                    targetValue = Offset.Zero,
                    animationSpec = spring(config.springDamping, config.springStiffness)
                )
            }
            launch {
                rotation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(config.springDamping, config.springStiffness)
                )
            }
        }
    }

    suspend fun resetInstant() {
        offset.snapTo(Offset.Zero)
        rotation.snapTo(0f)
        scale.snapTo(1f)
        hapticThresholdReached = false
    }
    
    fun getDirection(x: Float, y: Float): SwipeDirection? {
        val isHorizontal = abs(x) > abs(y)
        return if (isHorizontal) {
            if (x > 0) SwipeDirection.Right else SwipeDirection.Left
        } else {
            if (y > 0) SwipeDirection.Down else SwipeDirection.Up
        }
    }
}

@Composable
fun rememberSwipeCardState(
    config: SwipeConfig = SwipePresets.Juicy
): SwipeCardState {
    val scope = rememberCoroutineScope()
    return remember { SwipeCardState(config, scope) }
}
