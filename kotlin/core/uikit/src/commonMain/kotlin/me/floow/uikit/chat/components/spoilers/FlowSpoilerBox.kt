package me.floow.uikit.chat.components.spoilers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private class SpoilerParticle(
	val initialX: Float,
	val initialY: Float,
	val radiusDp: Float,
	val baseAlpha: Float,
	val seed: Float,
)

@Composable
fun FlowSpoilerBox(
	modifier: Modifier = Modifier,
	isRevealed: Boolean = false,
	backgroundColor: Color = Color.Unspecified,
	onReveal: () -> Unit = {},
	content: @Composable () -> Unit,
) {
	var localRevealed by remember(isRevealed) { mutableStateOf(isRevealed) }
	var tapOffset by remember { mutableStateOf<Offset?>(null) }
	val rippleProgress = remember { Animatable(if (isRevealed) 1f else 0f) }
	val coroutineScope = rememberCoroutineScope()

	val contentColor = LocalContentColor.current
	val surfaceColor = MaterialTheme.colorScheme.onSurface
	val particleColor = remember(contentColor, surfaceColor) {
		contentColor.takeOrElse { surfaceColor }
	}

	val infiniteTransition = rememberInfiniteTransition(label = "SpoilerNoise")
	val noiseTime by infiniteTransition.animateFloat(
		initialValue = 0f,
		targetValue = 6.2831855f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 1100, easing = LinearEasing),
			repeatMode = RepeatMode.Restart
		),
		label = "SpoilerNoiseTime"
	)

	val particles = remember {
		val random = Random(1337)
		List(140) {
			SpoilerParticle(
				initialX = random.nextFloat(),
				initialY = random.nextFloat(),
				radiusDp = 1.0f + random.nextFloat() * 1.6f,
				baseAlpha = when (random.nextInt(3)) {
					0 -> 0.35f
					1 -> 0.65f
					else -> 0.95f
				},
				seed = random.nextFloat() * 10f
			)
		}
	}

	LaunchedEffect(isRevealed) {
		if (isRevealed && rippleProgress.value < 1f) {
			localRevealed = true
			rippleProgress.animateTo(
				targetValue = 1f,
				animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
			)
		}
	}

	Box(modifier = modifier) {
		content()

		if (!localRevealed || rippleProgress.value < 1f) {
			Canvas(
				modifier = Modifier
					.matchParentSize()
					.clipToBounds()
					.pointerInput(localRevealed) {
						if (!localRevealed) {
							detectTapGestures { offset ->
								tapOffset = offset
								localRevealed = true
								coroutineScope.launch {
									onReveal()
									rippleProgress.animateTo(
										targetValue = 1f,
										animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
									)
								}
							}
						}
					}
			) {
				val currentRipple = rippleProgress.value
				val origin = tapOffset ?: Offset(size.width / 2f, size.height / 2f)
				val maxRadius = hypot(
					max(origin.x, size.width - origin.x),
					max(origin.y, size.height - origin.y)
				).coerceAtLeast(1f)
				val currentRadius = maxRadius * currentRipple

				val drawParticlesBlock: () -> Unit = {
					// Base opaque scrim to completely hide underlying text (Telegram style)
					if (backgroundColor != Color.Unspecified) {
						drawRect(
							color = backgroundColor.copy(alpha = (1f - currentRipple).coerceIn(0f, 1f))
						)
					} else {
						drawRect(
							color = particleColor.copy(alpha = (1f - currentRipple).coerceIn(0f, 1f))
						)
					}

					// Shimmering procedural noise particles
					val particleAlphaFade = (1f - currentRipple * 0.5f).coerceIn(0f, 1f)
					for (p in particles) {
						val px = (p.initialX * size.width + sin(noiseTime * 2.5f + p.seed) * 3.dp.toPx())
							.coerceIn(0f, size.width)
						val py = (p.initialY * size.height + cos(noiseTime * 2f + p.seed * 1.4f) * 3.dp.toPx())
							.coerceIn(0f, size.height)
						val pAlpha = (p.baseAlpha * (0.6f + 0.4f * sin(noiseTime * 4f + p.seed)) * particleAlphaFade)
							.coerceIn(0.1f, 1f)
						val pRadius = p.radiusDp.dp.toPx()

						drawCircle(
							color = particleColor.copy(alpha = pAlpha),
							radius = pRadius,
							center = Offset(px, py)
						)
					}

					// Wavefront spark ripple along expanding perimeter
					if (currentRipple in 0.01f..0.99f) {
						val sparkCount = 24
						val sparkFade = sin(currentRipple * 3.14159f)
						for (i in 0 until sparkCount) {
							val angle = (i.toFloat() / sparkCount) * 6.2831855f + noiseTime
							val sparkRadius = currentRadius + sin(angle * 6f + noiseTime * 3f) * 2.5.dp.toPx()
							val sx = origin.x + cos(angle) * sparkRadius
							val sy = origin.y + sin(angle) * sparkRadius
							if (sx in 0f..size.width && sy in 0f..size.height) {
								drawCircle(
									color = particleColor.copy(alpha = 0.9f * sparkFade),
									radius = 1.6.dp.toPx(),
									center = Offset(sx, sy)
								)
							}
						}
					}
				}

				if (currentRipple > 0f) {
					val revealPath = Path().apply {
						addOval(Rect(center = origin, radius = currentRadius))
					}
					clipPath(path = revealPath, clipOp = ClipOp.Difference) {
						drawParticlesBlock()
					}
				} else {
					drawParticlesBlock()
				}
			}
		}
	}
}
