package me.floow.uikit.components.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun ModalUnderlayContainer(
    isActive: Boolean,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
    extraTranslationX: () -> Float = { 0f },
    backdropModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxTranslationY = with(density) { 30.dp.toPx() }
    val underlayShape = remember { RoundedCornerShape(24.dp) }

    Box(modifier = modifier.fillMaxSize().then(backdropModifier)) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = progressProvider()
                        alpha = if (progress > 0.001f) 1f else 0f
                    }
                    .background(Color.Black)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isActive) {
                        val progress = progressProvider().coerceIn(0f, 1f)
                        scaleX = 1f - 0.09f * progress
                        scaleY = 1f - 0.09f * progress
                        translationY = maxTranslationY * progress
                        translationX = extraTranslationX()
                        if (progress > 0.01f) {
                            clip = true
                            shape = underlayShape
                        } else {
                            clip = false
                        }
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                        translationY = 0f
                        translationX = extraTranslationX()
                        clip = false
                    }
                }
        ) {
            content()
        }
    }
}
