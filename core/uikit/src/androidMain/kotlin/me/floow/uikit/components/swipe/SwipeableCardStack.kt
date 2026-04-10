package me.floow.uikit.components.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSwipeItemIndex = staticCompositionLocalOf { 0 }

/**
 * Modern, High-Performance Swipeable Card Stack with Infinite Stack Effect.
 * Implemented with a "Phantom Layer" architecture for zero interaction delay.
 * 
 * Updated to match the architecture of FeedStack with virtualization and advanced physics.
 */
@Composable
fun <T : Any> SwipeableCardStack(
    items: List<T>,
    onSwipe: (T, SwipeDirection) -> Unit,
    canDismissDirection: (SwipeDirection) -> Boolean = { true },
    onSwipeWithoutDismiss: (T, SwipeDirection) -> Unit = { _, _ -> },
    topCardExternalOffsetX: Float = 0f,
    modifier: Modifier = Modifier,
    config: SwipeConfig = SwipePresets.Juicy,
    keySelector: (T) -> Any = { it },
    clipShape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    overlayContent: @Composable (SwipeDirection, Float) -> Unit = { _, _ -> },
    content: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = rememberSwipeCardState(config)
    
    // Ghosts are cards that have been swiped away but are still animating
    val ghosts = remember { mutableStateListOf<GhostCardData<T>>() }

    // We only render the top maxVisibleItems items for performance
    val visibleItems = remember(items, config.maxVisibleItems) { 
        items.take(config.maxVisibleItems)
            .mapIndexed { index, item -> item to index }
            .reversed() 
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { state.containerSize = it.size },
        contentAlignment = Alignment.Center
    ) {
        // Render visible items
        visibleItems.forEach { (item, index) ->
            val isBottomCard = index == (config.maxVisibleItems - 1)
            val itemKey = keySelector(item)
            
            key(itemKey) {
                if (index == 0) {
                    // Top Card (Interactive)
                    DraggableStackCard(
                        item = item,
                        state = state,
                        config = config,
                        clipShape = clipShape,
                        topCardExternalOffsetX = topCardExternalOffsetX,
                        canDismissDirection = canDismissDirection,
                        onSwipe = { direction ->
                            ghosts.add(
                                GhostCardData(
                                    uniqueId = itemKey.toString(),
                                    item = item,
                                    startOffset = state.offset.value,
                                    startRotation = state.rotation.value,
                                    direction = direction
                                )
                            )
                            
                            // Prepare state for the next card (which is currently at index 1)
                            val nextOffsetDp = if (config.stackCardOffsets.isNotEmpty()) config.stackCardOffsets[0] else config.stackSpacing
                            val nextCardStartOffset = Offset(0f, with(density) { nextOffsetDp.toPx() })
                            
                            // Calculate scale for the next card (index 1)
                            val nextCardScale = if (config.stackCardScales.isNotEmpty()) {
                                config.stackCardScales.firstOrNull() ?: (1f - config.stackScaleMultiplier)
                            } else {
                                1f - config.stackScaleMultiplier
                            }
                            
                            val nextAlpha = if (config.stackCardAlphas.isNotEmpty()) config.stackCardAlphas[0] else config.stackAlphaMultiplier
                            val nextCardOverlayAlpha = nextAlpha.coerceIn(0f, 1f)
                            
                            scope.launch {
                                state.offset.snapTo(nextCardStartOffset)
                                state.scale.snapTo(nextCardScale)
                                state.rotation.snapTo(0f)
                                state.overlayAlpha.snapTo(nextCardOverlayAlpha)
                                
                                // Animate to the neutral position (Move Up effect)
                                launch { 
                                    state.offset.animateTo(
                                        targetValue = Offset.Zero,
                                        animationSpec = spring(
                                            dampingRatio = config.springDamping,
                                            stiffness = config.springStiffness
                                        )
                                    ) 
                                }
                                launch { 
                                    state.scale.animateTo(
                                        targetValue = 1f, 
                                        animationSpec = spring(
                                            dampingRatio = config.springDamping,
                                            stiffness = config.springStiffness
                                        )
                                    ) 
                                }
                                launch { 
                                    state.overlayAlpha.animateTo(
                                        targetValue = 0f, 
                                        animationSpec = spring(
                                            dampingRatio = config.springDamping,
                                            stiffness = config.springStiffness
                                        )
                                    ) 
                                }
                            }
                            
                            onSwipe(item, direction)
                        },
                        onSwipeWithoutDismiss = { direction ->
                            onSwipeWithoutDismiss(item, direction)
                        },
                        overlayContent = overlayContent,
                        content = content
                    )
                } else {
                    // Background Card (Passive)
                    BackgroundStackCard(
                        item = item,
                        index = index,
                        isBottomCard = isBottomCard,
                        config = config,
                        clipShape = clipShape,
                        content = content
                    )
                }
            }
        }
        
        // Render Ghosts (on top of everything)
        ghosts.forEach { ghost ->
            key(ghost.uniqueId) {
                GhostCard(
                    data = ghost,
                    config = config,
                    onAnimationEnd = { ghosts.remove(ghost) },
                    content = content
                )
            }
        }
    }
}

@Composable
private fun <T> DraggableStackCard(
    item: T,
    state: SwipeCardState,
    config: SwipeConfig,
    clipShape: androidx.compose.ui.graphics.Shape,
    topCardExternalOffsetX: Float,
    canDismissDirection: (SwipeDirection) -> Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    onSwipeWithoutDismiss: (SwipeDirection) -> Unit,
    overlayContent: @Composable (SwipeDirection, Float) -> Unit,
    content: @Composable (T) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = state.offset.value.x + topCardExternalOffsetX
                translationY = state.offset.value.y
                rotationZ = state.rotation.value
                scaleX = state.scale.value
                scaleY = state.scale.value
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { 
                        state.onDragStart()
                        if (config.hapticEnabled) {
                             haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = { 
                        val swiped = state.onDragEnd(
                            onSwipe = onSwipe,
                            canDismiss = canDismissDirection,
                            onSwipeWithoutDismiss = onSwipeWithoutDismiss
                        )
                        if (swiped && config.hapticEnabled) {
                             haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragCancel = { state.onDragCancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        
                        val wasThresholdReached = state.hapticThresholdReached
                        state.onDrag(dragAmount)
                        
                        // Check if we crossed threshold during this drag
                        if (config.hapticEnabled) {
                             val isNowReached = state.checkThreshold()
                             if (isNowReached && !wasThresholdReached) {
                                 haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                             } else if (!isNowReached && wasThresholdReached) {
                                 haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                             }
                        }
                    }
                )
            }
    ) {
        CompositionLocalProvider(LocalSwipeItemIndex provides 0) {
            content(item)
        }
        
        // Darkening overlay (for transition from background)
        if (state.overlayAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(clipShape)
                    .background(Color.Black.copy(alpha = state.overlayAlpha.value))
            )
        }
        
        // Overlay indicators
        if (state.isDragging) {
            val width = state.containerSize.width.toFloat().takeIf { it > 0f } ?: 1f
            val height = state.containerSize.height.toFloat().takeIf { it > 0f } ?: 1f
            val direction = state.getDirection(
                x = state.offset.value.x,
                y = state.offset.value.y
            ) ?: SwipeDirection.Right

            val rawProgress = when (direction) {
                SwipeDirection.Left, SwipeDirection.Right -> {
                    abs(state.offset.value.x) / (width * config.swipeThreshold)
                }

                SwipeDirection.Up -> {
                    abs(state.offset.value.y) / (height * config.swipeThresholdUp)
                }

                SwipeDirection.Down -> {
                    abs(state.offset.value.y) / (height * config.swipeThresholdDown)
                }
            }.coerceIn(0f, 1f)

            // Faster visual response at gesture start, without changing swipe thresholds.
            val easedProgress = 1f - (1f - rawProgress) * (1f - rawProgress)

            overlayContent(direction, easedProgress)
        }
    }
}

@Composable
private fun <T> BackgroundStackCard(
    item: T,
    index: Int,
    isBottomCard: Boolean,
    config: SwipeConfig,
    clipShape: androidx.compose.ui.graphics.Shape,
    content: @Composable (T) -> Unit
) {
    val density = LocalDensity.current
    
    // Target calculations
    val targetScale = if (index - 1 < config.stackCardScales.size && index > 0) {
        config.stackCardScales[index - 1]
    } else {
        1f - (index * config.stackScaleMultiplier)
    }
    
    val targetRotation = if (isBottomCard) {
        0f
    } else if (index - 1 < config.stackCardRotations.size && index > 0) {
        config.stackCardRotations[index - 1]
    } else {
        0f
    }
    
    val targetOffsetY = if (index - 1 < config.stackCardOffsets.size && index > 0) {
        config.stackCardOffsets[index - 1]
    } else {
        (index * config.stackSpacing.value).dp
    }
    
    // Entry animation values
    val startScale = if (isBottomCard) targetScale - 0.1f else targetScale
    val startOffsetY = if (isBottomCard) targetOffsetY.value + 50f else targetOffsetY.value
    val startAlpha = if (isBottomCard) 0f else 1f
    
    val scale = remember { Animatable(startScale) }
    val offsetY = remember { Animatable(startOffsetY) }
    val alpha = remember { Animatable(startAlpha) }
    
    // Animate overlay opacity
    val targetOverlayAlpha = if (index - 1 < config.stackCardAlphas.size && index > 0) {
        config.stackCardAlphas[index - 1]
    } else {
        (index * config.stackAlphaMultiplier).coerceIn(0f, 1f)
    }
    val overlayAlpha = remember { Animatable(targetOverlayAlpha) }
    
    val springSpec = spring<Float>(
        dampingRatio = config.springDamping,
        stiffness = config.springStiffness
    )
    
    LaunchedEffect(targetScale, targetOffsetY, targetOverlayAlpha) {
        launch { scale.animateTo(targetScale, springSpec) }
        launch { offsetY.animateTo(targetOffsetY.value, springSpec) }
        launch { alpha.animateTo(1f, springSpec) }
        launch { overlayAlpha.animateTo(targetOverlayAlpha, springSpec) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationY = offsetY.value * density.density // Convert dp to px manually if needed, or use with(density)
                rotationZ = targetRotation
                this.alpha = alpha.value
            }
    ) {
        CompositionLocalProvider(LocalSwipeItemIndex provides index) {
            content(item)
        }
        
        // Darkening overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(clipShape)
                .background(Color.Black.copy(alpha = overlayAlpha.value))
        )
    }
}

private data class GhostCardData<T>(
    val uniqueId: String,
    val item: T,
    val startOffset: Offset,
    val startRotation: Float,
    val direction: SwipeDirection
)

@Composable
private fun <T> GhostCard(
    data: GhostCardData<T>,
    config: SwipeConfig,
    onAnimationEnd: () -> Unit,
    content: @Composable (T) -> Unit
) {
    val offset = remember { Animatable(data.startOffset, Offset.VectorConverter) }
    
    LaunchedEffect(Unit) {
        val targetX = when (data.direction) {
            SwipeDirection.Left -> -config.dismissDistance
            SwipeDirection.Right -> config.dismissDistance
            else -> 0f
        }
        val targetY = when (data.direction) {
            SwipeDirection.Up -> -config.dismissDistance
            SwipeDirection.Down -> config.dismissDistance
            else -> data.startOffset.y
        }
        
        launch {
            offset.animateTo(
                targetValue = Offset(targetX, targetY),
                animationSpec = tween(config.dismissDuration)
            )
            onAnimationEnd()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                rotationZ = data.startRotation
            }
    ) {
        CompositionLocalProvider(LocalSwipeItemIndex provides 0) {
            content(data.item)
        }
    }
}
