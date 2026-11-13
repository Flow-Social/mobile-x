package me.floow.shared.chats.media.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

enum class EditorCropPreset(val label: String, val ratio: Float?) {
    FREE("Свободный", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_NINE("16:9", 16f / 9f)
}

enum class CropDragHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    MOVE
}

data class NormalizedPoint(val x: Float, val y: Float)

data class DrawStroke(
    val points: List<NormalizedPoint>,
    val color: Color,
    val relativeStrokeWidth: Float,
)

data class LoadedEditorImage(
    val bitmap: ImageBitmap,
    val width: Int,
    val height: Int,
)

interface ChatImageProcessor {
    suspend fun processAndSaveImage(
        sourcePath: String,
        rotationDegrees: Float,
        strokes: List<DrawStroke>,
        cropRectNormalized: Rect,
        maxDimension: Int = 1920,
    ): String?

    suspend fun getImageDimensions(sourcePath: String): Pair<Int, Int>
}

@Composable
expect fun rememberChatImageProcessor(): ChatImageProcessor
