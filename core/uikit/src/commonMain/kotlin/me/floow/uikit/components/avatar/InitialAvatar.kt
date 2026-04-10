package me.floow.uikit.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.floow.uikit.theme.LocalTypography

@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    backgroundColor: Color = Color.LightGray,
    textColor: Color = Color.Black,
    textStyle: TextStyle? = null,
    shape: Shape = CircleShape
) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = textStyle ?: LocalTypography.current.labelMedium,
            color = textColor
        )
    }
}

@Composable
fun NetworkAvatar(
    name: String,
    avatarModel: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    backgroundColor: Color = Color.LightGray,
    textColor: Color = Color.Black,
    textStyle: TextStyle? = null,
    shape: Shape = CircleShape,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null
) {
    val normalizedModel = when (avatarModel) {
        null -> null
        is String -> avatarModel.trim().takeIf { it.isNotEmpty() }
        else -> avatarModel
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        InitialAvatar(
            name = name,
            size = size,
            backgroundColor = backgroundColor,
            textColor = textColor,
            textStyle = textStyle,
            shape = shape,
            modifier = Modifier.fillMaxSize()
        )

        if (normalizedModel != null) {
            AsyncImage(
                model = normalizedModel,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
