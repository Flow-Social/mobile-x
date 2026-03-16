package me.floow.uikit.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.*
import me.floow.uikit.util.ComponentPreviewBox
import kotlin.math.max

private const val Cookie9SidedDeeperInnerRadius = 0.74f

private val cookie9SidedPolygon: RoundedPolygon = RoundedPolygon.star(
    numVerticesPerRadius = 9,
    innerRadius = Cookie9SidedDeeperInnerRadius,
    rounding = CornerRounding(0.5f),
)

private val elevanagon = cookie9SidedPolygon

private val ninehedron = cookie9SidedPolygon

private fun RoundedPolygon.getBounds() = calculateBounds().let { Rect(it[0], it[1], it[2], it[3]) }

class RoundedPolygonShape(
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

val ElevanagonShape = RoundedPolygonShape(polygon = elevanagon)

val NinehedronShape = RoundedPolygonShape(polygon = ninehedron)

@Preview
@Composable
fun ElevanagonPolygonShapePreview(modifier: Modifier = Modifier) {
    ComponentPreviewBox {
        Box(
            Modifier
                .size(64.dp)
                .clip(ElevanagonShape)
                .background(Color.Red)
        )
    }
}

@Preview
@Composable
fun NinehedronPolygonShapePreview(modifier: Modifier = Modifier) {
    ComponentPreviewBox {
        Box(
            Modifier
                .size(64.dp)
                .clip(NinehedronShape)
                .background(Color.Red)
        )
    }
}
