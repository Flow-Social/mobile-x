package me.floow.uikit.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.*
import kotlin.math.max

private const val FlowStarInnerRadius = 0.74f

private val flowStarPolygon: RoundedPolygon = RoundedPolygon.star(
    numVerticesPerRadius = 9,
    innerRadius = FlowStarInnerRadius,
    rounding = CornerRounding(0.5f),
)

private fun RoundedPolygon.getBounds() = calculateBounds().let { Rect(it[0], it[1], it[2], it[3]) }

private class RoundedPolygonShape(
    private val polygon: RoundedPolygon
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = polygon.toPath().asComposePath()
        val matrix = Matrix()
        val bounds = polygon.getBounds()
        val maxDimension = max(bounds.width, bounds.height)
        matrix.scale(size.width / maxDimension, size.height / maxDimension)
        matrix.translate(-bounds.left, -bounds.top)

        path.transform(matrix)
        return Outline.Generic(path)
    }
}

actual fun getFlowStarShape(): Shape = RoundedPolygonShape(flowStarPolygon)

val ElevanagonShape: Shape
    get() = getFlowStarShape()
