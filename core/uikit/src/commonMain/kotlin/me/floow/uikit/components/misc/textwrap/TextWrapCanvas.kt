package me.floow.uikit.components.misc.textwrap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.max

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

    val textMeasurer = rememberTextMeasurer()

    val result = measureText(
        text = text,
        obstacleAlignment = obstacleAlignment,
        textMeasurer = textMeasurer,
        obstacleSize = obstacleSize,
        layoutWidth = constraints.maxWidth,
        softWrap = softWrap,
        mergedStyle = mergedStyle,
    )

    val canvasSize = calculateCanvasSize(
        density = LocalDensity.current,
        result = result,
        obstacleSize = obstacleSize,
        constraints = constraints,
    )

    val affectedTextLeftHorizontalOffset =
        if (checkIfStartAlignment(obstacleAlignment)) obstacleSize.width.toFloat() else 0f

    if (checkIfTopAlignment(obstacleAlignment)) {
        Canvas(modifier = Modifier.size(canvasSize)) {
            translate(left = affectedTextLeftHorizontalOffset) {
                result.affectedTextResult?.multiParagraph?.paint(
                    canvas = drawContext.canvas,
                    color = color,
                    decoration = textDecoration,
                )
            }

            translate(top = result.affectedTextHeight.toFloat()) {
                result.unaffectedTextResult?.multiParagraph?.paint(
                    canvas = drawContext.canvas,
                    color = color,
                    decoration = textDecoration,
                )
            }
        }
    } else {
        Canvas(modifier = Modifier.size(canvasSize)) {
            translate {
                result.unaffectedTextResult?.multiParagraph?.paint(
                    canvas = drawContext.canvas,
                    color = color,
                    decoration = textDecoration,
                )
            }

            translate(
                top = result.unaffectedTextHeight.toFloat(),
                left = affectedTextLeftHorizontalOffset
            ) {
                result.affectedTextResult?.multiParagraph?.paint(
                    canvas = drawContext.canvas,
                    color = color,
                    decoration = textDecoration,
                )
            }
        }
    }
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
): TextFlowCanvasLayoutResult {
    if (checkIfTopAlignment(obstacleAlignment)) {
        return textMeasurer.measureTopAlignmentTextFlow(
            text = text,
            obstacleSize = obstacleSize,
            layoutWidth = layoutWidth,
            softWrap = softWrap,
            mergedStyle = mergedStyle,
        )
    } else {
        return textMeasurer.measureBottomAlignmentTextFlow(
            text = text,
            obstacleSize = obstacleSize,
            layoutWidth = layoutWidth,
            softWrap = softWrap,
            mergedStyle = mergedStyle,
        )
    }
}

private fun TextMeasurer.measureTopAlignmentTextFlow(
    text: AnnotatedString,
    obstacleSize: IntSize,
    layoutWidth: Int,
    softWrap: Boolean,
    mergedStyle: TextStyle,
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
): TextFlowCanvasLayoutResult {
    var bottomAffectedBlock: TextLayoutResult? = null
    var topUnaffectedBlock: TextLayoutResult? = null

    var hasTopUnaffectedBlock = true
    var bottomAffectedBlockFirstCharIndex = text.lastIndex

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

        if (unaffectedText.last() == '\n') unaffectedText =
            AnnotatedString(unaffectedText.dropLast(1).toString())

        topUnaffectedBlock = measure(
            text = unaffectedText,
            style = mergedStyle,
            constraints = Constraints(
                maxWidth = layoutWidth,
                maxHeight = Int.MAX_VALUE,
            ),
            softWrap = softWrap,
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
