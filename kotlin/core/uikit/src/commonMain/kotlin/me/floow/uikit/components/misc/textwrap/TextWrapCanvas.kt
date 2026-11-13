package me.floow.uikit.components.misc.textwrap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val MAX_SPOILER_PARTICLES = 120
private const val OFFSCREEN_COORD = -9999f
private const val SPARK_COUNT = 14

private class SpoilerShape(
    val path: Path,
    val bounds: Rect,
    val stepX: Float,
    val stepY: Float,
    val baseCount: Int,
    val baseLutIndices: IntArray,
    val baseColRow: IntArray,
    val basePoints: FloatArray,
    val glintCount: Int,
    val glintLutIndices: IntArray,
    val glintColRow: IntArray,
    val glintPoints: FloatArray,
    val totalCount: Int,
    val blastPoints: FloatArray,
)

private class SpoilerLut(
    val jitterX: FloatArray,
    val jitterY: FloatArray,
    val radiusPx: FloatArray,
    val phase: FloatArray,
    val baseAlpha: FloatArray,
)

@Composable
internal fun TextWrapCanvas(
    text: AnnotatedString,
    obstacleSize: IntSize,
    obstacleAlignment: TextWrapObstacleAlignment,
    constraints: Constraints,
    style: TextStyle,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    isSpoilerRevealed: Boolean = false,
    onRevealSpoiler: (() -> Unit)? = null,
    onProfileTagClick: ((String) -> Unit)? = null,
    onPostLinkClick: ((String, String) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null,
    onTextClick: (() -> Unit)? = null,
) {
    val mergedStyle = style.merge(
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            fontFamily = fontFamily,
            textDecoration = textDecoration,
            fontStyle = fontStyle,
            letterSpacing = letterSpacing,
        ),
    )

    val contentColor = LocalContentColor.current
    val resolvedColor = color.takeOrElse {
        mergedStyle.color.takeOrElse {
            contentColor.takeOrElse {
                Color(0xFF888888)
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()

    val result = measureText(
        text = text,
        obstacleAlignment = obstacleAlignment,
        textMeasurer = textMeasurer,
        obstacleSize = obstacleSize,
        layoutWidth = constraints.maxWidth,
        softWrap = softWrap,
        mergedStyle = mergedStyle,
        maxLines = maxLines,
        overflow = overflow,
    )

    val density = LocalDensity.current

    val canvasSize = calculateCanvasSize(
        density = density,
        result = result,
        obstacleSize = obstacleSize,
        constraints = constraints,
    )

    val affectedTextLeftHorizontalOffset =
        if (checkIfStartAlignment(obstacleAlignment)) obstacleSize.width.toFloat() else 0f

    var localRevealed by remember(isSpoilerRevealed) { mutableStateOf(isSpoilerRevealed) }
    var tapOffset by remember { mutableStateOf<Offset?>(null) }
    val rippleProgress = remember(isSpoilerRevealed) { Animatable(if (isSpoilerRevealed) 1f else 0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isSpoilerRevealed) {
        if (isSpoilerRevealed && rippleProgress.value < 1f) {
            localRevealed = true
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing)
            )
        }
    }

    val hasSpoilersInText = remember(text) {
        text.getStringAnnotations(TEXT_TAG_SPOILER, 0, text.length).isNotEmpty()
    }

    val uriHandler = LocalUriHandler.current

    val currentRipple = rippleProgress.value
    val isVisualFullyRevealed = localRevealed && currentRipple >= 1f

    val noiseTime = if (hasSpoilersInText && !isVisualFullyRevealed) {
        val infiniteTransition = rememberInfiniteTransition(label = "InlineSpoilerNoise")
        val animatedTime by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.2831855f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "InlineSpoilerNoiseTime"
        )
        animatedTime
    } else {
        0f
    }

    val spoilerLut = remember(density) {
        val random = Random(1337)
        val lutSize = 128
        val jX = FloatArray(lutSize) { 0.15f + random.nextFloat() * 0.70f }
        val jY = FloatArray(lutSize) { 0.15f + random.nextFloat() * 0.70f }
        val rPx = FloatArray(lutSize) { with(density) { (0.95f + random.nextFloat() * 0.75f).dp.toPx() } }
        val ph = FloatArray(lutSize) { random.nextFloat() * 6.2831855f }
        val bAlpha = FloatArray(lutSize) { 0.60f + random.nextFloat() * 0.35f }
        SpoilerLut(jX, jY, rPx, ph, bAlpha)
    }

    val stepPx = remember(density) { with(density) { 4.6.dp.toPx() } }
    val driftPx = remember(density) { with(density) { 1.0.dp.toPx() } }
    val sparkReachLimit = remember(density) { with(density) { 12.dp.toPx() } }

    val spoilerCornerRadiusPx = remember(density) { with(density) { 8.dp.toPx() } }

    val topSpoilerShapes = remember(result.unaffectedTextResult, isVisualFullyRevealed, spoilerCornerRadiusPx, stepPx, spoilerLut) {
        val res = result.unaffectedTextResult
        if (isVisualFullyRevealed || res == null) emptyList()
        else {
            val t = res.layoutInput.text
            t.getStringAnnotations(TEXT_TAG_SPOILER, 0, t.length).flatMap { ann ->
                createRoundedSpoilerShapes(res, ann.start, ann.end, spoilerCornerRadiusPx, stepPx, spoilerLut)
            }
        }
    }

    val bottomSpoilerShapes = remember(result.affectedTextResult, isVisualFullyRevealed, spoilerCornerRadiusPx, stepPx, spoilerLut) {
        val res = result.affectedTextResult
        if (isVisualFullyRevealed || res == null) emptyList()
        else {
            val t = res.layoutInput.text
            t.getStringAnnotations(TEXT_TAG_SPOILER, 0, t.length).flatMap { ann ->
                createRoundedSpoilerShapes(res, ann.start, ann.end, spoilerCornerRadiusPx, stepPx, spoilerLut)
            }
        }
    }

    val topSpoilerUnionPath = remember(topSpoilerShapes) {
        Path().apply {
            topSpoilerShapes.forEach { addPath(it.path) }
        }
    }

    val bottomSpoilerUnionPath = remember(bottomSpoilerShapes) {
        Path().apply {
            bottomSpoilerShapes.forEach { addPath(it.path) }
        }
    }

    val hasActiveSpoilers = !localRevealed && (topSpoilerShapes.isNotEmpty() || bottomSpoilerShapes.isNotEmpty()) && onRevealSpoiler != null
    val hasLinksInText = remember(text) {
        text.getStringAnnotations(TEXT_TAG_URL, 0, text.length).isNotEmpty() ||
        text.getStringAnnotations(TEXT_TAG_PROFILE, 0, text.length).isNotEmpty() ||
        text.getStringAnnotations(TEXT_TAG_POST, 0, text.length).isNotEmpty() ||
        text.getStringAnnotations(TEXT_TAG_TRAILING, 0, text.length).isNotEmpty()
    }
    val hasClickable = hasLinksInText || onProfileTagClick != null || onPostLinkClick != null || onUrlClick != null || onTrailingClick != null || onTextClick != null

    val pointerModifier = if (hasActiveSpoilers || hasClickable) {
        Modifier.pointerInput(hasActiveSpoilers, topSpoilerShapes, bottomSpoilerShapes, localRevealed) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pos = down.position

                if (hasActiveSpoilers) {
                    val hitTop = topSpoilerShapes.any { it.bounds.contains(pos) }
                    val affectedOffset = if (checkIfTopAlignment(obstacleAlignment)) {
                        Offset(affectedTextLeftHorizontalOffset, 0f)
                    } else {
                        Offset(affectedTextLeftHorizontalOffset, result.unaffectedTextHeight.toFloat())
                    }
                    val hitBottom = bottomSpoilerShapes.any { it.bounds.contains(pos - affectedOffset) }

                    if (hitTop || hitBottom) {
                        down.consume()
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            tapOffset = pos
                            localRevealed = true
                            coroutineScope.launch {
                                rippleProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing)
                                )
                                onRevealSpoiler?.invoke()
                            }
                        }
                        return@awaitEachGesture
                    }
                }

                val up = waitForUpOrCancellation()
                if (up != null) {
                    val clickPos = up.position
                    val isTopAlign = checkIfTopAlignment(obstacleAlignment)

                    val hitResult: Pair<TextLayoutResult, Offset>? = if (isTopAlign) {
                        val affHeight = result.affectedTextHeight.toFloat()
                        if (clickPos.y < affHeight && result.affectedTextResult != null) {
                            result.affectedTextResult to Offset(affectedTextLeftHorizontalOffset, 0f)
                        } else if (result.unaffectedTextResult != null) {
                            result.unaffectedTextResult to Offset(0f, affHeight)
                        } else null
                    } else {
                        val unaffHeight = result.unaffectedTextHeight.toFloat()
                        if (clickPos.y < unaffHeight && result.unaffectedTextResult != null) {
                            result.unaffectedTextResult to Offset.Zero
                        } else if (result.affectedTextResult != null) {
                            result.affectedTextResult to Offset(affectedTextLeftHorizontalOffset, unaffHeight)
                        } else null
                    }

                    if (hitResult != null) {
                        val (layoutRes, offset) = hitResult
                        val relativePos = clickPos - offset
                        val charOffset = layoutRes.getOffsetForPosition(relativePos)
                        val fullText = layoutRes.layoutInput.text

                        var handled = false
                        fullText.getStringAnnotations(TEXT_TAG_POST, 0, fullText.length)
                            .firstOrNull { charOffset in it.start until it.end }?.let { ann ->
                                val parts = ann.item.split("/", limit = 2)
                                if (parts.size == 2) {
                                    onPostLinkClick?.invoke(parts[0], parts[1])
                                    handled = true
                                }
                            }
                        if (!handled) {
                            fullText.getStringAnnotations(TEXT_TAG_PROFILE, 0, fullText.length)
                                .firstOrNull { charOffset in it.start until it.end }?.let { ann ->
                                    onProfileTagClick?.invoke(ann.item)
                                    handled = true
                                }
                        }
                        if (!handled) {
                            fullText.getStringAnnotations(TEXT_TAG_URL, 0, fullText.length)
                                .firstOrNull { charOffset in it.start until it.end }?.let { ann ->
                                    if (onUrlClick != null) {
                                        onUrlClick.invoke(ann.item)
                                    } else {
                                        runCatching { uriHandler.openUri(ann.item) }
                                    }
                                    handled = true
                                }
                        }
                        if (!handled) {
                            fullText.getStringAnnotations(TEXT_TAG_TRAILING, 0, fullText.length)
                                .firstOrNull { charOffset in it.start until it.end }?.let {
                                    onTrailingClick?.invoke()
                                    handled = true
                                }
                        }

                        if (handled) {
                            up.consume()
                        } else {
                            onTextClick?.let {
                                it.invoke()
                                up.consume()
                            }
                        }
                    } else {
                        onTextClick?.let {
                            it.invoke()
                            up.consume()
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    val blastReach = remember(density) { with(density) { 14.dp.toPx() } }

    val basePaint = remember {
        Paint().apply {
            this.style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
        }
    }
    val glintPaint = remember {
        Paint().apply {
            this.style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
        }
    }
    val blastPaint = remember {
        Paint().apply {
            this.style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
        }
    }
    val sparkPaint = remember {
        Paint().apply {
            this.style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
        }
    }

    val sparkPoints = remember { FloatArray(SPARK_COUNT * 2) }
    val driftX = remember { FloatArray(128) }
    val driftY = remember { FloatArray(128) }

    basePaint.color = resolvedColor.copy(alpha = 0.42f)
    basePaint.strokeWidth = with(density) { 2.6.dp.toPx() }
    glintPaint.color = resolvedColor.copy(alpha = 0.82f)
    glintPaint.strokeWidth = with(density) { 2.8.dp.toPx() }
    blastPaint.strokeWidth = with(density) { 2.4.dp.toPx() }
    sparkPaint.strokeWidth = with(density) { 2.2.dp.toPx() }

    val lut = spoilerLut
    // 30 FPS квантование фонового шума (как в Telegram: renderDelayMs = 33ms)
    val noiseQuantum = 6.2831855f / 108f // 108 шагов за 3600 мс ~ 30 FPS
    val currentNoiseStep = (noiseTime / noiseQuantum).toInt()
    var lastCalculatedStep by remember { mutableStateOf(-1) }

    if (currentNoiseStep != lastCalculatedStep) {
        lastCalculatedStep = currentNoiseStep
        val quantizedTime = currentNoiseStep * noiseQuantum
        for (i in 0 until 128) {
            val ph = quantizedTime + lut.phase[i]
            driftX[i] = sin(ph) * driftPx
            driftY[i] = cos(ph) * driftPx
        }
    }

    val drawSpoilerOnShape: DrawScope.(SpoilerShape, Offset, Float, Float, Float, Offset) -> Unit = drawSpoilerOnShape@{ shape, blockOffset, curRadius, curRadiusSq, bReach, origin ->
        val bounds = shape.bounds
        val bCount = shape.baseCount
        val gCount = shape.glintCount
        val basePts = shape.basePoints
        val glintPts = shape.glintPoints
        val stepX = shape.stepX
        val stepY = shape.stepY

        if (currentRipple <= 0f) {
            for (i in 0 until bCount) {
                val c = shape.baseColRow[i * 2]
                val r = shape.baseColRow[i * 2 + 1]
                val idx = shape.baseLutIndices[i]
                basePts[i * 2] = bounds.left + (c + lut.jitterX[idx]) * stepX + driftX[idx]
                basePts[i * 2 + 1] = bounds.top + (r + lut.jitterY[idx]) * stepY + driftY[idx]
            }
            for (i in 0 until gCount) {
                val c = shape.glintColRow[i * 2]
                val r = shape.glintColRow[i * 2 + 1]
                val idx = shape.glintLutIndices[i]
                glintPts[i * 2] = bounds.left + (c + lut.jitterX[idx]) * stepX + driftX[idx]
                glintPts[i * 2 + 1] = bounds.top + (r + lut.jitterY[idx]) * stepY + driftY[idx]
            }

            clipPath(shape.path) {
                if (bCount > 0) drawContext.canvas.drawRawPoints(PointMode.Points, basePts, basePaint)
                if (gCount > 0) drawContext.canvas.drawRawPoints(PointMode.Points, glintPts, glintPaint)
            }
        } else {
            // Быстрая проверка границ строки относительно волны
            val dxFar = maxOf(kotlin.math.abs(origin.x - bounds.left), kotlin.math.abs(origin.x - bounds.right))
            val dyFar = maxOf(kotlin.math.abs(origin.y - bounds.top), kotlin.math.abs(origin.y - bounds.bottom))
            val maxLineDist = hypot(dxFar, dyFar)

            // Если строка уже полностью растворилась позади волны — пропускаем
            if (curRadius - bReach > maxLineDist) {
                return@drawSpoilerOnShape
            }

            val dxNear = if (origin.x < bounds.left) bounds.left - origin.x else if (origin.x > bounds.right) origin.x - bounds.right else 0f
            val dyNear = if (origin.y < bounds.top) bounds.top - origin.y else if (origin.y > bounds.bottom) origin.y - bounds.bottom else 0f
            val minLineDistSq = dxNear * dxNear + dyNear * dyNear

            // Если волна еще вообще не дошла до этой строки — рисуем точки в покое
            if (curRadiusSq < minLineDistSq) {
                for (i in 0 until bCount) {
                    val c = shape.baseColRow[i * 2]
                    val r = shape.baseColRow[i * 2 + 1]
                    val idx = shape.baseLutIndices[i]
                    basePts[i * 2] = bounds.left + (c + lut.jitterX[idx]) * stepX + driftX[idx]
                    basePts[i * 2 + 1] = bounds.top + (r + lut.jitterY[idx]) * stepY + driftY[idx]
                }
                for (i in 0 until gCount) {
                    val c = shape.glintColRow[i * 2]
                    val r = shape.glintColRow[i * 2 + 1]
                    val idx = shape.glintLutIndices[i]
                    glintPts[i * 2] = bounds.left + (c + lut.jitterX[idx]) * stepX + driftX[idx]
                    glintPts[i * 2 + 1] = bounds.top + (r + lut.jitterY[idx]) * stepY + driftY[idx]
                }
                clipPath(shape.path) {
                    if (bCount > 0) drawContext.canvas.drawRawPoints(PointMode.Points, basePts, basePaint)
                    if (gCount > 0) drawContext.canvas.drawRawPoints(PointMode.Points, glintPts, glintPaint)
                }
                return@drawSpoilerOnShape
            }

            // Активная зона взрыва волны: быстрая векторная алгебра без atan2/cos/sin
            val blastPts = shape.blastPoints
            var blastIndex = 0

            for (i in 0 until bCount) {
                val c = shape.baseColRow[i * 2]
                val r = shape.baseColRow[i * 2 + 1]
                val idx = shape.baseLutIndices[i]
                val px = bounds.left + (c + lut.jitterX[idx]) * stepX + driftX[idx]
                val py = bounds.top + (r + lut.jitterY[idx]) * stepY + driftY[idx]

                val dx = px - origin.x
                val dy = py - origin.y
                val distSq = dx * dx + dy * dy

                if (distSq > curRadiusSq) {
                    basePts[i * 2] = px
                    basePts[i * 2 + 1] = py
                    blastPts[blastIndex * 2] = OFFSCREEN_COORD
                    blastPts[blastIndex * 2 + 1] = OFFSCREEN_COORD
                } else {
                    basePts[i * 2] = OFFSCREEN_COORD
                    basePts[i * 2 + 1] = OFFSCREEN_COORD
                    val dist = kotlin.math.sqrt(distSq)
                    val diff = curRadius - dist
                    if (diff < bReach) {
                        val progress = diff / bReach
                        val invDist = if (dist > 0.001f) 1f / dist else 0f
                        val dirX = dx * invDist
                        val dirY = dy * invDist
                        val flyDist = bReach * (progress * (1.5f - 0.5f * progress))
                        val jitter = (lut.jitterX[idx] - 0.5f) * 0.7f
                        blastPts[blastIndex * 2] = px + (dirX - dirY * jitter) * flyDist
                        blastPts[blastIndex * 2 + 1] = py + (dirY + dirX * jitter) * flyDist
                    } else {
                        blastPts[blastIndex * 2] = OFFSCREEN_COORD
                        blastPts[blastIndex * 2 + 1] = OFFSCREEN_COORD
                    }
                }
                blastIndex++
            }

            for (i in 0 until gCount) {
                val c = shape.glintColRow[i * 2]
                val r = shape.glintColRow[i * 2 + 1]
                val idx = shape.glintLutIndices[i]
                val px = bounds.left + (c + lut.jitterX[idx]) * stepX + driftX[idx]
                val py = bounds.top + (r + lut.jitterY[idx]) * stepY + driftY[idx]

                val dx = px - origin.x
                val dy = py - origin.y
                val distSq = dx * dx + dy * dy

                if (distSq > curRadiusSq) {
                    glintPts[i * 2] = px
                    glintPts[i * 2 + 1] = py
                    blastPts[blastIndex * 2] = OFFSCREEN_COORD
                    blastPts[blastIndex * 2 + 1] = OFFSCREEN_COORD
                } else {
                    glintPts[i * 2] = OFFSCREEN_COORD
                    glintPts[i * 2 + 1] = OFFSCREEN_COORD
                    val dist = kotlin.math.sqrt(distSq)
                    val diff = curRadius - dist
                    if (diff < bReach) {
                        val progress = diff / bReach
                        val invDist = if (dist > 0.001f) 1f / dist else 0f
                        val dirX = dx * invDist
                        val dirY = dy * invDist
                        val flyDist = bReach * (progress * (1.5f - 0.5f * progress))
                        val jitter = (lut.jitterX[idx] - 0.5f) * 0.7f
                        blastPts[blastIndex * 2] = px + (dirX - dirY * jitter) * flyDist
                        blastPts[blastIndex * 2 + 1] = py + (dirY + dirX * jitter) * flyDist
                    } else {
                        blastPts[blastIndex * 2] = OFFSCREEN_COORD
                        blastPts[blastIndex * 2 + 1] = OFFSCREEN_COORD
                    }
                }
                blastIndex++
            }

            clipPath(shape.path) {
                if (bCount > 0) drawContext.canvas.drawRawPoints(PointMode.Points, basePts, basePaint)
                if (gCount > 0) drawContext.canvas.drawRawPoints(PointMode.Points, glintPts, glintPaint)
            }

            val blastAlpha = ((1f - currentRipple) * 0.85f).coerceIn(0f, 1f)
            if (blastAlpha > 0.01f) {
                blastPaint.color = resolvedColor.copy(alpha = blastAlpha)
                drawContext.canvas.drawRawPoints(PointMode.Points, blastPts, blastPaint)
            }
        }
    }

    val showSpoilers = !isVisualFullyRevealed

    val renderTextBlock: DrawScope.(
        TextLayoutResult?,
        List<SpoilerShape>,
        Path,
        Offset,
        Float,
        Float
    ) -> Unit = { textResult, shapes, unionPath, blockOffset, tx, ty ->
        if (textResult != null) {
            translate(left = tx, top = ty) {
                if (showSpoilers && shapes.isNotEmpty()) {
                    clipPath(unionPath, clipOp = ClipOp.Difference) {
                        textResult.multiParagraph.paint(
                            canvas = drawContext.canvas,
                            color = resolvedColor,
                            decoration = textDecoration,
                        )
                    }
                    if (currentRipple > 0f) {
                        clipPath(unionPath) {
                            textResult.multiParagraph.paint(
                                canvas = drawContext.canvas,
                                color = resolvedColor.copy(alpha = currentRipple),
                                decoration = textDecoration,
                            )
                        }
                    }
                    val origin = tapOffset?.let { it - blockOffset } ?: (shapes.firstOrNull()?.bounds?.center ?: Offset.Zero)
                    val maxRadius = if (currentRipple > 0f) {
                        shapes.fold(0f) { acc, s ->
                            val b = s.bounds
                            val r = hypot(
                                maxOf(kotlin.math.abs(origin.x - b.left), kotlin.math.abs(origin.x - b.right)),
                                maxOf(kotlin.math.abs(origin.y - b.top), kotlin.math.abs(origin.y - b.bottom))
                            )
                            maxOf(acc, r)
                        }.coerceAtLeast(1f)
                    } else 0f
                    val curRadius = maxRadius * currentRipple
                    val curRadiusSq = curRadius * curRadius

                    shapes.forEach { shape ->
                        drawSpoilerOnShape(shape, blockOffset, curRadius, curRadiusSq, blastReach, origin)
                    }

                    if (currentRipple in 0.01f..0.85f) {
                        val sparkFade = sin(currentRipple * 3.14159f) * (1f - currentRipple)
                        val sparkRadiusBase = curRadius
                        for (i in 0 until SPARK_COUNT) {
                            val angle = (i.toFloat() / SPARK_COUNT) * 6.2831855f + noiseTime * 0.5f
                            val sparkRadius = sparkRadiusBase + sin(angle * 4f + noiseTime) * 1.5f * density.density
                            sparkPoints[i * 2] = origin.x + cos(angle) * sparkRadius
                            sparkPoints[i * 2 + 1] = origin.y + sin(angle) * sparkRadius
                        }
                        sparkPaint.color = resolvedColor.copy(alpha = (0.75f * sparkFade).coerceIn(0f, 1f))
                        drawContext.canvas.drawRawPoints(PointMode.Points, sparkPoints, sparkPaint)
                    }
                } else {
                    textResult.multiParagraph.paint(
                        canvas = drawContext.canvas,
                        color = resolvedColor,
                        decoration = textDecoration,
                    )
                }
            }
        }
    }

    if (checkIfTopAlignment(obstacleAlignment)) {
        Canvas(modifier = Modifier.size(canvasSize).then(pointerModifier)) {
            renderTextBlock(
                result.affectedTextResult,
                bottomSpoilerShapes,
                bottomSpoilerUnionPath,
                Offset(affectedTextLeftHorizontalOffset, 0f),
                affectedTextLeftHorizontalOffset,
                0f
            )
            renderTextBlock(
                result.unaffectedTextResult,
                topSpoilerShapes,
                topSpoilerUnionPath,
                Offset(0f, result.affectedTextHeight.toFloat()),
                0f,
                result.affectedTextHeight.toFloat()
            )
        }
    } else {
        Canvas(modifier = Modifier.size(canvasSize).then(pointerModifier)) {
            renderTextBlock(
                result.unaffectedTextResult,
                topSpoilerShapes,
                topSpoilerUnionPath,
                Offset.Zero,
                0f,
                0f
            )
            renderTextBlock(
                result.affectedTextResult,
                bottomSpoilerShapes,
                bottomSpoilerUnionPath,
                Offset(affectedTextLeftHorizontalOffset, result.unaffectedTextHeight.toFloat()),
                affectedTextLeftHorizontalOffset,
                result.unaffectedTextHeight.toFloat()
            )
        }
    }
}

private fun createRoundedSpoilerShapes(
    layoutResult: TextLayoutResult,
    start: Int,
    end: Int,
    cornerRadiusPx: Float,
    stepPx: Float,
    lut: SpoilerLut,
): List<SpoilerShape> {
    val textLength = layoutResult.layoutInput.text.length
    val clampedStart = start.coerceIn(0, textLength)
    val clampedEnd = end.coerceIn(0, textLength)
    if (clampedStart >= clampedEnd) return emptyList()

    val startLine = layoutResult.getLineForOffset(clampedStart)
    val endLine = layoutResult.getLineForOffset((clampedEnd - 1).coerceAtLeast(clampedStart))
    val shapes = mutableListOf<SpoilerShape>()
    val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)

    for (line in startLine..endLine) {
        val lineTop = layoutResult.getLineTop(line)
        val lineBottom = layoutResult.getLineBottom(line)
        if (lineBottom <= lineTop) continue

        val lineVisibleEnd = layoutResult.getLineEnd(line, visibleEnd = true)
        val lineStart = maxOf(clampedStart, layoutResult.getLineStart(line))
        val lineEnd = minOf(clampedEnd, lineVisibleEnd)

        val rect: Rect = if (lineStart < lineEnd) {
            val linePath = layoutResult.getPathForRange(lineStart, lineEnd)
            val bounds = linePath.getBounds()
            if (bounds.isEmpty || bounds.width <= 1f) {
                if (line in (startLine + 1) until endLine) {
                    val prevRight = if (shapes.isNotEmpty()) shapes.last().bounds.right else layoutResult.size.width.toFloat()
                    val nextLineEnd = layoutResult.getLineEnd(minOf(line + 1, endLine), visibleEnd = true)
                    val nextLineStart = layoutResult.getLineStart(minOf(line + 1, endLine))
                    val nextRight = if (nextLineStart < nextLineEnd) layoutResult.getPathForRange(nextLineStart, nextLineEnd).getBounds().right else prevRight
                    val left = if (shapes.isNotEmpty()) shapes.last().bounds.left else 0f
                    val right = maxOf(prevRight, nextRight).coerceAtLeast(stepPx * 4)
                    Rect(left, lineTop, right, lineBottom)
                } else {
                    continue
                }
            } else {
                Rect(
                    left = bounds.left - 2f,
                    top = lineTop,
                    right = bounds.right + 2f,
                    bottom = lineBottom
                )
            }
        } else {
            // Пустая строка между абзацами внутри спойлера
            if (line in (startLine + 1) until endLine) {
                val prevRight = if (shapes.isNotEmpty()) shapes.last().bounds.right else layoutResult.size.width.toFloat()
                val nextLineEnd = layoutResult.getLineEnd(minOf(line + 1, endLine), visibleEnd = true)
                val nextLineStart = layoutResult.getLineStart(minOf(line + 1, endLine))
                val nextRight = if (nextLineStart < nextLineEnd) layoutResult.getPathForRange(nextLineStart, nextLineEnd).getBounds().right else prevRight
                val left = if (shapes.isNotEmpty()) shapes.last().bounds.left else 0f
                val right = maxOf(prevRight, nextRight).coerceAtLeast(stepPx * 4)
                Rect(left, lineTop, right, lineBottom)
            } else {
                continue
            }
        }

        val roundRect = when {
            startLine == endLine -> RoundRect(rect, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
            line == startLine -> RoundRect(
                rect = rect,
                topLeft = cornerRadius,
                topRight = cornerRadius,
                bottomRight = CornerRadius.Zero,
                bottomLeft = CornerRadius.Zero,
            )
            line == endLine -> RoundRect(
                rect = rect,
                topLeft = CornerRadius.Zero,
                topRight = CornerRadius.Zero,
                bottomRight = cornerRadius,
                bottomLeft = cornerRadius,
            )
            else -> RoundRect(rect, CornerRadius.Zero, CornerRadius.Zero, CornerRadius.Zero, CornerRadius.Zero)
        }

        val path = Path().apply {
            addRoundRect(roundRect)
        }

        val cols = maxOf(2, minOf(72, (rect.width / stepPx).toInt() + 1))
        val rows = maxOf(1, minOf(5, ((rect.height / stepPx) + 0.5f).toInt()))
        val stepX = rect.width / cols
        val stepY = rect.height / rows

        var bCount = 0
        var gCount = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = (c * 19 + r * 41 + line * 31) and 127
                if (lut.baseAlpha[idx] < 0.72f) bCount++ else gCount++
            }
        }

        val baseLutIndices = IntArray(bCount)
        val baseColRow = IntArray(bCount * 2)
        val glintLutIndices = IntArray(gCount)
        val glintColRow = IntArray(gCount * 2)

        var bIdx = 0
        var gIdx = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = (c * 19 + r * 41 + line * 31) and 127
                if (lut.baseAlpha[idx] < 0.72f) {
                    baseLutIndices[bIdx] = idx
                    baseColRow[bIdx * 2] = c
                    baseColRow[bIdx * 2 + 1] = r
                    bIdx++
                } else {
                    glintLutIndices[gIdx] = idx
                    glintColRow[gIdx * 2] = c
                    glintColRow[gIdx * 2 + 1] = r
                    gIdx++
                }
            }
        }

        val basePoints = FloatArray(bCount * 2)
        val glintPoints = FloatArray(gCount * 2)
        val blastPoints = FloatArray((bCount + gCount) * 2)

        shapes.add(
            SpoilerShape(
                path = path,
                bounds = rect,
                stepX = stepX,
                stepY = stepY,
                baseCount = bCount,
                baseLutIndices = baseLutIndices,
                baseColRow = baseColRow,
                basePoints = basePoints,
                glintCount = gCount,
                glintLutIndices = glintLutIndices,
                glintColRow = glintColRow,
                glintPoints = glintPoints,
                totalCount = bCount + gCount,
                blastPoints = blastPoints,
            )
        )
    }
    return shapes
}

private class TextFlowCanvasLayoutResult(
    val affectedTextResult: TextLayoutResult?,
    val unaffectedTextResult: TextLayoutResult?,
) {
    val affectedTextHeight: Int get() = affectedTextResult?.size?.height ?: 0
    val unaffectedTextHeight: Int get() = unaffectedTextResult?.size?.height ?: 0
    val affectedTextWidth: Int get() = affectedTextResult?.size?.width ?: 0
    val unaffectedTextWidth: Int get() = unaffectedTextResult?.size?.width ?: 0
}

private fun measureText(
    text: AnnotatedString,
    obstacleAlignment: TextWrapObstacleAlignment,
    obstacleSize: IntSize,
    layoutWidth: Int,
    softWrap: Boolean,
    mergedStyle: TextStyle,
    textMeasurer: TextMeasurer,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
): TextFlowCanvasLayoutResult {
    if (checkIfTopAlignment(obstacleAlignment)) {
        return textMeasurer.measureTopAlignmentTextFlow(
            text = text,
            obstacleSize = obstacleSize,
            layoutWidth = layoutWidth,
            softWrap = softWrap,
            mergedStyle = mergedStyle,
            maxLines = maxLines,
            overflow = overflow,
        )
    } else {
        return textMeasurer.measureBottomAlignmentTextFlow(
            text = text,
            obstacleSize = obstacleSize,
            layoutWidth = layoutWidth,
            softWrap = softWrap,
            mergedStyle = mergedStyle,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

private fun TextMeasurer.measureTopAlignmentTextFlow(
    text: AnnotatedString,
    obstacleSize: IntSize,
    layoutWidth: Int,
    softWrap: Boolean,
    mergedStyle: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
): TextFlowCanvasLayoutResult {
    var topAffectedBlock: TextLayoutResult? = null
    var bottomUnaffectedBlock: TextLayoutResult? = null

    val topAffectedBlockVisibleLineCount: Int
    var topAffectedBlockLastCharIndex = -1
    var hasBottomUnaffectedBlock = true

    if (obstacleSize.height > 0) {
        val constrainedMeasuredText = measure(
            text = text,
            style = mergedStyle,
            constraints = Constraints(
                maxWidth = layoutWidth - obstacleSize.width,
                maxHeight = Int.MAX_VALUE,
            ),
            softWrap = softWrap,
        )

        val lastVisibleLineIndex = constrainedMeasuredText.lastVisibleLineIndex(obstacleSize.height)
        val topBlockHeight = constrainedMeasuredText.getLineBottom(lastVisibleLineIndex)
        topAffectedBlockVisibleLineCount = lastVisibleLineIndex + 1

        topAffectedBlockLastCharIndex = constrainedMeasuredText.getOffsetForPosition(
            Offset(
                constrainedMeasuredText.getLineRight(lastVisibleLineIndex),
                constrainedMeasuredText.getLineTop(lastVisibleLineIndex),
            ),
        )

        hasBottomUnaffectedBlock = topAffectedBlockLastCharIndex < text.length

        topAffectedBlock = measure(
            text = text,
            style = mergedStyle,
            constraints = Constraints(
                maxWidth = layoutWidth - obstacleSize.width,
                maxHeight = topBlockHeight.toInt(),
            ),
            softWrap = softWrap,
            maxLines = topAffectedBlockVisibleLineCount,
        )
    }

    if (hasBottomUnaffectedBlock) {
        bottomUnaffectedBlock = measure(
            text = text.subSequence(topAffectedBlockLastCharIndex + 1, text.length),
            style = mergedStyle,
            constraints = Constraints(
                maxWidth = layoutWidth,
                maxHeight = Int.MAX_VALUE,
            ),
            softWrap = softWrap,
            maxLines = maxLines,
            overflow = overflow,
        )
    }

    return TextFlowCanvasLayoutResult(
        topAffectedBlock,
        bottomUnaffectedBlock,
    )
}

private fun TextMeasurer.measureBottomAlignmentTextFlow(
    text: AnnotatedString,
    obstacleSize: IntSize,
    layoutWidth: Int,
    softWrap: Boolean,
    mergedStyle: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
): TextFlowCanvasLayoutResult {
    var bottomAffectedBlock: TextLayoutResult? = null
    var topUnaffectedBlock: TextLayoutResult? = null

    var hasTopUnaffectedBlock = true
    var bottomAffectedBlockFirstCharIndex = text.length

    if (obstacleSize.height > 0) {
        val measuredText = measure(
            text = text,
            style = mergedStyle,
            constraints = Constraints(
                maxWidth = layoutWidth,
                maxHeight = Int.MAX_VALUE,
            ),
            softWrap = softWrap,
        )

        val constrainedMeasuredText = measure(
            text = text,
            style = mergedStyle,
            constraints = Constraints(
                maxWidth = layoutWidth - obstacleSize.width,
                maxHeight = Int.MAX_VALUE,
            ),
            softWrap = softWrap,
        )

        val lastLineIndex = measuredText.lineCount - 1
        val affectedLinesCount =
            constrainedMeasuredText.lineCount - constrainedMeasuredText.lastVisibleLineIndex(
                constrainedMeasuredText.size.height - obstacleSize.height
            )

        for (lineCount in 0..affectedLinesCount) {
            if (bottomAffectedBlock != null && bottomAffectedBlock.lineCount >= affectedLinesCount) break

            bottomAffectedBlockFirstCharIndex =
                measuredText.getLineStart(lastLineIndex - lineCount)

            bottomAffectedBlock = measure(
                text = text.subSequence(bottomAffectedBlockFirstCharIndex, text.length),
                style = mergedStyle,
                constraints = Constraints(
                    maxWidth = layoutWidth - obstacleSize.width,
                    maxHeight = Int.MAX_VALUE,
                ),
                softWrap = softWrap,
            )
        }

        hasTopUnaffectedBlock =
            bottomAffectedBlockFirstCharIndex > 0
    }

    if (hasTopUnaffectedBlock) {
        var unaffectedText = text.subSequence(0, bottomAffectedBlockFirstCharIndex)

        if (unaffectedText.isNotEmpty() && unaffectedText.last() == '\n') unaffectedText =
            unaffectedText.subSequence(0, unaffectedText.length - 1)

        topUnaffectedBlock = measure(
            text = unaffectedText,
            style = mergedStyle,
            constraints = Constraints(
                maxWidth = layoutWidth,
                maxHeight = Int.MAX_VALUE,
            ),
            softWrap = softWrap,
            maxLines = maxLines,
            overflow = overflow,
        )
    }

    return TextFlowCanvasLayoutResult(
        bottomAffectedBlock,
        topUnaffectedBlock,
    )
}

private fun TextLayoutResult.lastVisibleLineIndex(height: Int): Int {
    repeat(lineCount) {
        if (getLineBottom(it) > height) {
            return it
        }
    }
    return lineCount - 1
}

private fun calculateCanvasSize(
    density: Density,
    result: TextFlowCanvasLayoutResult,
    obstacleSize: IntSize,
    constraints: Constraints,
): DpSize {

    val affectedBlockWidth = result.affectedTextWidth + obstacleSize.width
    val unaffectedBlockWidth = result.unaffectedTextWidth

    val width = max(affectedBlockWidth, unaffectedBlockWidth)
    val height = result.affectedTextHeight + result.unaffectedTextHeight

    return with(density) {
        DpSize(
            width = constraints.constrainWidth(width).toDp(),
            height = constraints.constrainHeight(height).toDp(),
        )
    }
}

private fun checkIfTopAlignment(
    obstacleAlignment: TextWrapObstacleAlignment,
): Boolean =
    obstacleAlignment == TextWrapObstacleAlignment.TopStart || obstacleAlignment == TextWrapObstacleAlignment.TopEnd

private fun checkIfStartAlignment(
    obstacleAlignment: TextWrapObstacleAlignment,
): Boolean =
    obstacleAlignment == TextWrapObstacleAlignment.TopStart || obstacleAlignment == TextWrapObstacleAlignment.BottomStart
