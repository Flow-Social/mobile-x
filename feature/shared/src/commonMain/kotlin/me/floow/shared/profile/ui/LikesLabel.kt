package me.floow.shared.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.likes_count
import flow.feature.shared.generated.resources.no_likes_yet
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun LikesLabel(
    onClick: () -> Unit,
    totalLikesReceived: Int,
    modifier: Modifier = Modifier
) {
    val text = if (totalLikesReceived <= 0) {
        stringResource(Res.string.no_likes_yet)
    } else {
        pluralStringResource(
            Res.plurals.likes_count,
            totalLikesReceived,
            totalLikesReceived.toShortenedString()
        )
    }
    val shape = RoundedCornerShape(100.dp)

    Box(
        modifier = modifier.wrapContentWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-18).dp, y = 4.dp)
                .rotate(-8f)
                .size(27.dp)
        )

        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFB33333),
                            Color(0xFF240C0C)
                        )
                    )
                )
                .border(2.dp, Color(0x40121212), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = LocalTypography.current.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 1.dp, y = (-9).dp)
                .rotate(14f)
                .size(22.dp)
        )
    }
}

private fun Int.toShortenedString(): String {
    return when {
        this >= 1_000_000 -> "${(this / 100_000) / 10.0}M"
        this >= 1_000 -> "${(this / 100) / 10.0}k"
        else -> toString()
    }
}
