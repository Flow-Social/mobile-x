package me.floow.uikit.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val FlowStarVertices = 9
private const val FlowStarInnerRadius = 0.74f
private const val FlowStarCornerRounding = 0.5f

actual fun getFlowStarShape(): Shape = GenericShape { size, _ ->
    val outerRadius = min(size.width, size.height) / 2f
    val innerRadius = outerRadius * FlowStarInnerRadius
    val center = Offset(size.width / 2f, size.height / 2f)
    val points = buildList(capacity = FlowStarVertices * 2) {
        repeat(FlowStarVertices * 2) { index ->
            val radius = if (index % 2 == 0) outerRadius else innerRadius
            val angle = (-PI / 2.0) + index * PI / FlowStarVertices
            add(
                Offset(
                    x = center.x + radius * cos(angle).toFloat(),
                    y = center.y + radius * sin(angle).toFloat()
                )
            )
        }
    }

    addPath(
        buildRoundedStarPath(
            points = points,
            cornerRounding = FlowStarCornerRounding
        )
    )
}

private fun buildRoundedStarPath(
    points: List<Offset>,
    cornerRounding: Float,
): Path {
    val path = Path()
    if (points.size < 3) return path

    points.indices.forEach { index ->
        val previous = points[(index - 1 + points.size) % points.size]
        val current = points[index]
        val next = points[(index + 1) % points.size]

        val incoming = current - previous
        val outgoing = next - current
        val incomingLength = incoming.length()
        val outgoingLength = outgoing.length()
        val cornerInset = min(incomingLength, outgoingLength) * 0.18f * cornerRounding

        val start = current - incoming.normalized() * cornerInset
        val end = current + outgoing.normalized() * cornerInset

        if (index == 0) {
            path.moveTo(start.x, start.y)
        } else {
            path.lineTo(start.x, start.y)
        }
        path.quadraticTo(current.x, current.y, end.x, end.y)
    }

    path.close()
    return path
}

private fun Offset.length(): Float =
    kotlin.math.sqrt(x * x + y * y)

private fun Offset.normalized(): Offset {
    val length = length()
    if (length == 0f) return Offset.Zero
    return Offset(x / length, y / length)
}
