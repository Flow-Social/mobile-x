package me.floow.shared.chats.media.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.floow.shared.chats.ui.PlatformBackHandler
import me.floow.shared.profile.image.createLocalImageFileReader
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
fun ChatImageEditorScreen(
    sourceUri: String,
    onApply: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(enabled = true, onBack = onCancel)
    val scope = rememberCoroutineScope()
    val processor = rememberChatImageProcessor()
    val imageReader = remember { createLocalImageFileReader() }

    var sourceImage by remember { mutableStateOf<LoadedEditorImage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var activePreset by remember { mutableStateOf(EditorCropPreset.FREE) }
    var cropRectNormalized by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var isDrawingMode by remember { mutableStateOf(false) }

    val strokes = remember { mutableStateListOf<DrawStroke>() }
    val currentStrokePoints = remember { mutableStateListOf<NormalizedPoint>() }
    var currentColor by remember { mutableStateOf(Color.Red) }

    LaunchedEffect(sourceUri) {
        isLoading = true
        try {
            val file = imageReader.read(sourceUri)
            if (file != null && file.bytes.isNotEmpty()) {
                val bitmap = file.bytes.decodeToImageBitmap()
                sourceImage = LoadedEditorImage(bitmap = bitmap, width = bitmap.width, height = bitmap.height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    fun updateCropRectForPreset(preset: EditorCropPreset, bmpWidth: Int, bmpHeight: Int, rotation: Float) {
        val rotatedW = if (rotation == 90f || rotation == 270f) bmpHeight else bmpWidth
        val rotatedH = if (rotation == 90f || rotation == 270f) bmpWidth else bmpHeight
        val targetRatio = preset.ratio
        if (targetRatio == null || targetRatio <= 0f) {
            cropRectNormalized = Rect(0f, 0f, 1f, 1f)
            return
        }
        val imgAspect = rotatedW.toFloat() / rotatedH.toFloat()
        cropRectNormalized = if (imgAspect > targetRatio) {
            val normW = targetRatio / imgAspect
            val left = (1f - normW) / 2f
            Rect(left = left, top = 0f, right = left + normW, bottom = 1f)
        } else {
            val normH = imgAspect / targetRatio
            val top = (1f - normH) / 2f
            Rect(left = 0f, top = top, right = 1f, bottom = top + normH)
        }
    }

    val availableColors = remember {
        listOf(Color.White, Color.Black, Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF007AFF))
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            return@Surface
        }

        val img = sourceImage
        if (img == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Не удалось загрузить фото", color = Color.White)
            }
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top control bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel, enabled = !isSaving) {
                    Icon(Icons.Default.Close, contentDescription = "Отмена", tint = Color.White)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isDrawingMode && strokes.isNotEmpty()) {
                        IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Отменить", tint = Color.White)
                        }
                    }

                    IconButton(onClick = { isDrawingMode = !isDrawingMode }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Рисование",
                            tint = if (isDrawingMode) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }

                    IconButton(onClick = {
                        rotationDegrees = (rotationDegrees + 90f) % 360f
                        updateCropRectForPreset(activePreset, img.width, img.height, rotationDegrees)
                    }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Повернуть", tint = Color.White)
                    }

                    IconButton(
                        onClick = {
                            if (isSaving) return@IconButton
                            isSaving = true
                            scope.launch {
                                val resultPath = processor.processAndSaveImage(
                                    sourcePath = sourceUri,
                                    rotationDegrees = rotationDegrees,
                                    strokes = strokes.toList(),
                                    cropRectNormalized = cropRectNormalized
                                )
                                isSaving = false
                                if (resultPath != null) {
                                    onApply(resultPath)
                                } else {
                                    onCancel()
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Готово", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Canvas area
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val isRotated90or270 = rotationDegrees == 90f || rotationDegrees == 270f
                val currentW = if (isRotated90or270) img.height else img.width
                val currentH = if (isRotated90or270) img.width else img.height
                val bmpAspect = currentW.toFloat() / currentH.toFloat()

                val containerAspect = maxWidth / maxHeight
                val (displayW, displayH) = if (containerAspect > bmpAspect) {
                    val h = maxHeight
                    Pair(h * bmpAspect, h)
                } else {
                    val w = maxWidth
                    Pair(w, w / bmpAspect)
                }

                val density = LocalDensity.current
                val displayWPx = with(density) { displayW.toPx() }
                val displayHPx = with(density) { displayH.toPx() }

                Box(
                    modifier = Modifier
                        .size(displayW, displayH)
                        .clipToBounds()
                        .then(
                            if (isDrawingMode) {
                                Modifier.pointerInput(currentColor, displayWPx, displayHPx) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentStrokePoints.clear()
                                            val nx = (offset.x / displayWPx).coerceIn(0f, 1f)
                                            val ny = (offset.y / displayHPx).coerceIn(0f, 1f)
                                            currentStrokePoints.add(NormalizedPoint(nx, ny))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val nx = (change.position.x / displayWPx).coerceIn(0f, 1f)
                                            val ny = (change.position.y / displayHPx).coerceIn(0f, 1f)
                                            currentStrokePoints.add(NormalizedPoint(nx, ny))
                                        },
                                        onDragEnd = {
                                            if (currentStrokePoints.isNotEmpty()) {
                                                strokes.add(
                                                    DrawStroke(
                                                        points = currentStrokePoints.toList(),
                                                        color = currentColor,
                                                        relativeStrokeWidth = 6.dp.toPx() / displayWPx
                                                    )
                                                )
                                                currentStrokePoints.clear()
                                            }
                                        },
                                        onDragCancel = {
                                            currentStrokePoints.clear()
                                        }
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // Image Canvas with rotation
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        rotate(rotationDegrees, pivot = center) {
                            val drawW = if (isRotated90or270) size.height else size.width
                            val drawH = if (isRotated90or270) size.width else size.height
                            drawImage(
                                image = img.bitmap,
                                dstOffset = IntOffset(
                                    x = ((size.width - drawW) / 2f).toInt(),
                                    y = ((size.height - drawH) / 2f).toInt()
                                ),
                                dstSize = IntSize(drawW.toInt(), drawH.toInt())
                            )
                        }
                    }

                    // Stroke overlays
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (stroke in strokes) {
                            if (stroke.points.size > 1) {
                                val p = Path().apply {
                                    moveTo(stroke.points[0].x * size.width, stroke.points[0].y * size.height)
                                    for (i in 1 until stroke.points.size) {
                                        lineTo(stroke.points[i].x * size.width, stroke.points[i].y * size.height)
                                    }
                                }
                                drawPath(
                                    path = p,
                                    color = stroke.color,
                                    style = Stroke(
                                        width = stroke.relativeStrokeWidth * size.width,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        if (currentStrokePoints.size > 1) {
                            val p = Path().apply {
                                moveTo(currentStrokePoints[0].x * size.width, currentStrokePoints[0].y * size.height)
                                for (i in 1 until currentStrokePoints.size) {
                                    lineTo(currentStrokePoints[i].x * size.width, currentStrokePoints[i].y * size.height)
                                }
                            }
                            drawPath(
                                path = p,
                                color = currentColor,
                                style = Stroke(
                                    width = 6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Crop overlay
                    if (!isDrawingMode) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val left = cropRectNormalized.left * size.width
                            val top = cropRectNormalized.top * size.height
                            val right = cropRectNormalized.right * size.width
                            val bottom = cropRectNormalized.bottom * size.height
                            val rectW = right - left
                            val rectH = bottom - top

                            // Scrim
                            drawRect(Color(0x88000000), topLeft = Offset.Zero, size = Size(size.width, top))
                            drawRect(Color(0x88000000), topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                            drawRect(Color(0x88000000), topLeft = Offset(0f, top), size = Size(left, rectH))
                            drawRect(Color(0x88000000), topLeft = Offset(right, top), size = Size(size.width - right, rectH))

                            // Crop frame
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(left, top),
                                size = Size(rectW, rectH),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Corner handles
                            val handleLen = 20.dp.toPx()
                            val handleStroke = 4.dp.toPx()
                            val handleCircleRadius = 5.dp.toPx()

                            // TL
                            drawLine(Color.White, Offset(left, top), Offset(left + handleLen, top), handleStroke)
                            drawLine(Color.White, Offset(left, top), Offset(left, top + handleLen), handleStroke)
                            drawCircle(Color.White, radius = handleCircleRadius, center = Offset(left, top))
                            // TR
                            drawLine(Color.White, Offset(right, top), Offset(right - handleLen, top), handleStroke)
                            drawLine(Color.White, Offset(right, top), Offset(right, top + handleLen), handleStroke)
                            drawCircle(Color.White, radius = handleCircleRadius, center = Offset(right, top))
                            // BL
                            drawLine(Color.White, Offset(left, bottom), Offset(left + handleLen, bottom), handleStroke)
                            drawLine(Color.White, Offset(left, bottom), Offset(left, bottom - handleLen), handleStroke)
                            drawCircle(Color.White, radius = handleCircleRadius, center = Offset(left, bottom))
                            // BR
                            drawLine(Color.White, Offset(right, bottom), Offset(right - handleLen, bottom), handleStroke)
                            drawLine(Color.White, Offset(right, bottom), Offset(right, bottom + handleLen), handleStroke)
                            drawCircle(Color.White, radius = handleCircleRadius, center = Offset(right, bottom))
                        }

                        // Drag gestures for crop frame
                        var activeCropDragHandle by remember { mutableStateOf(CropDragHandle.NONE) }
                        if (activePreset == EditorCropPreset.FREE) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(displayWPx, displayHPx) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val cur = cropRectNormalized
                                                val leftPx = cur.left * displayWPx
                                                val topPx = cur.top * displayHPx
                                                val rightPx = cur.right * displayWPx
                                                val bottomPx = cur.bottom * displayHPx
                                                val touchRadius = 40.dp.toPx()

                                                activeCropDragHandle = when {
                                                    (offset - Offset(leftPx, topPx)).getDistance() < touchRadius -> CropDragHandle.TOP_LEFT
                                                    (offset - Offset(rightPx, topPx)).getDistance() < touchRadius -> CropDragHandle.TOP_RIGHT
                                                    (offset - Offset(leftPx, bottomPx)).getDistance() < touchRadius -> CropDragHandle.BOTTOM_LEFT
                                                    (offset - Offset(rightPx, bottomPx)).getDistance() < touchRadius -> CropDragHandle.BOTTOM_RIGHT
                                                    offset.x in leftPx..rightPx && offset.y in topPx..bottomPx -> CropDragHandle.MOVE
                                                    else -> CropDragHandle.NONE
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val dxNorm = dragAmount.x / displayWPx
                                                val dyNorm = dragAmount.y / displayHPx
                                                val cur = cropRectNormalized
                                                val minSize = 0.1f

                                                cropRectNormalized = when (activeCropDragHandle) {
                                                    CropDragHandle.TOP_LEFT -> {
                                                        val nLeft = (cur.left + dxNorm).coerceIn(0f, cur.right - minSize)
                                                        val nTop = (cur.top + dyNorm).coerceIn(0f, cur.bottom - minSize)
                                                        Rect(nLeft, nTop, cur.right, cur.bottom)
                                                    }
                                                    CropDragHandle.TOP_RIGHT -> {
                                                        val nRight = (cur.right + dxNorm).coerceIn(cur.left + minSize, 1f)
                                                        val nTop = (cur.top + dyNorm).coerceIn(0f, cur.bottom - minSize)
                                                        Rect(cur.left, nTop, nRight, cur.bottom)
                                                    }
                                                    CropDragHandle.BOTTOM_LEFT -> {
                                                        val nLeft = (cur.left + dxNorm).coerceIn(0f, cur.right - minSize)
                                                        val nBottom = (cur.bottom + dyNorm).coerceIn(cur.top + minSize, 1f)
                                                        Rect(nLeft, cur.top, cur.right, nBottom)
                                                    }
                                                    CropDragHandle.BOTTOM_RIGHT -> {
                                                        val nRight = (cur.right + dxNorm).coerceIn(cur.left + minSize, 1f)
                                                        val nBottom = (cur.bottom + dyNorm).coerceIn(cur.top + minSize, 1f)
                                                        Rect(cur.left, cur.top, nRight, nBottom)
                                                    }
                                                    CropDragHandle.MOVE -> {
                                                        val w = cur.width
                                                        val h = cur.height
                                                        val nLeft = (cur.left + dxNorm).coerceIn(0f, 1f - w)
                                                        val nTop = (cur.top + dyNorm).coerceIn(0f, 1f - h)
                                                        Rect(nLeft, nTop, nLeft + w, nTop + h)
                                                    }
                                                    CropDragHandle.NONE -> cur
                                                }
                                            },
                                            onDragEnd = { activeCropDragHandle = CropDragHandle.NONE },
                                            onDragCancel = { activeCropDragHandle = CropDragHandle.NONE }
                                        )
                                    }
                            )
                        } else {
                            // Fixed aspect ratio drag
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(displayWPx, displayHPx) {
                                        detectDragGestures(
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val dxNorm = dragAmount.x / displayWPx
                                                val dyNorm = dragAmount.y / displayHPx
                                                val cur = cropRectNormalized
                                                val w = cur.width
                                                val h = cur.height
                                                val nLeft = (cur.left + dxNorm).coerceIn(0f, 1f - w)
                                                val nTop = (cur.top + dyNorm).coerceIn(0f, 1f - h)
                                                cropRectNormalized = Rect(nLeft, nTop, nLeft + w, nTop + h)
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            // Bottom toolbar
            if (isDrawingMode) {
                // Color palette
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (color in availableColors) {
                        val isSelected = currentColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else if (color == Color.Black) Modifier.border(1.dp, Color.Gray, CircleShape)
                                    else Modifier
                                )
                                .clickable { currentColor = color }
                        )
                    }
                }
            } else {
                // Crop preset selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (preset in EditorCropPreset.entries) {
                        val selected = activePreset == preset
                        Text(
                            text = preset.label,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0x33FFFFFF) else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable {
                                    activePreset = preset
                                    img.let {
                                        updateCropRectForPreset(preset, it.width, it.height, rotationDegrees)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}
