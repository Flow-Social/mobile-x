package me.floow.uikit.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import me.floow.uikit.components.media.FlowImageSource
import me.floow.uikit.components.media.ProgressiveImage
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
    imageSource: FlowImageSource,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    backgroundColor: Color = Color.LightGray,
    textColor: Color = Color.Black,
    textStyle: TextStyle? = null,
    shape: Shape = CircleShape,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null
) {
    NetworkAvatar(
        name = name,
        avatarModel = imageSource.url,
        modifier = modifier,
        avatarThumb = imageSource.microThumb,
        size = size,
        backgroundColor = backgroundColor,
        textColor = textColor,
        textStyle = textStyle,
        shape = shape,
        contentScale = contentScale,
        contentDescription = contentDescription
    )
}

@Composable
fun NetworkAvatar(
    name: String,
    avatarModel: Any?,
    modifier: Modifier = Modifier,
    avatarThumb: String? = null,
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
    val cleanThumb = avatarThumb?.trim()?.takeIf { it.isNotEmpty() }

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

        var avatarLoadFailed by remember(normalizedModel) { mutableStateOf(false) }
        if (!avatarLoadFailed && (normalizedModel != null || cleanThumb != null)) {
            if (normalizedModel is String || normalizedModel == null) {
                ProgressiveImage(
                    thumbUrl = cleanThumb,
                    targetUrl = normalizedModel as? String,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    placeholderColor = Color.Transparent,
                    modifier = Modifier.fillMaxSize(),
                    onError = { avatarLoadFailed = true }
                )
            } else {
                AsyncImage(
                    model = normalizedModel,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    onError = { avatarLoadFailed = true }
                )
            }
        }
    }
}

@Composable
fun NetworkAvatar(
    name: String,
    thumbUrl: String?,
    targetUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    backgroundColor: Color = Color.LightGray,
    textColor: Color = Color.Black,
    textStyle: TextStyle? = null,
    shape: Shape = CircleShape,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null
) {
    NetworkAvatar(
        name = name,
        avatarModel = targetUrl,
        modifier = modifier,
        avatarThumb = thumbUrl,
        size = size,
        backgroundColor = backgroundColor,
        textColor = textColor,
        textStyle = textStyle,
        shape = shape,
        contentScale = contentScale,
        contentDescription = contentDescription
    )
}

